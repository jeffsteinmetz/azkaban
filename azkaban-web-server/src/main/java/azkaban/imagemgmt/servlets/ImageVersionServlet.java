/*
 * Copyright 2020 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package azkaban.imagemgmt.servlets;

import azkaban.Constants.ImageMgmtConstants;
import azkaban.server.HttpRequestUtils;
import azkaban.server.session.Session;
import azkaban.webapp.AzkabanWebServer;
import azkaban.webapp.servlet.LoginAbstractAzkabanServlet;
import azkaban.imagemgmt.exeception.ImageMgmtValidationException;
import azkaban.imagemgmt.dto.RequestContext;
import azkaban.imagemgmt.services.ImageVersionService;
import com.linkedin.jersey.api.uri.UriTemplate;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.HttpStatus;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This servlet exposes the REST APIs such as create, get etc. for image type. Below are the
 * supported APIs.
 * Create Image Type API: POST /imageVersions?session.id=? --data @payload.json
 * Get Image Type API:
 * GET /imageVersions?session.id=?&imageType=?&imageVersion=?
 * GET /imageTypes/{id}?session.id=?
 */
public class ImageVersionServlet extends LoginAbstractAzkabanServlet {
  private static final String GET_IMAGE_VERSION_URI = "/imageVersions";
  private static final String IMAGE_VERSION_ID_KEY = "id";
  private static final UriTemplate CREATE_IMAGE_VERSION_URI_TEMPLATE = new UriTemplate(
      String.format("/imageVersions/{%s}", IMAGE_VERSION_ID_KEY));
  private ImageVersionService imageVersionService;
  private ObjectMapper objectMapper;

  private static final Logger log = LoggerFactory.getLogger(ImageVersionServlet.class);

  public ImageVersionServlet() {
    super(new ArrayList<>());
  }

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);
    final AzkabanWebServer server = (AzkabanWebServer) getApplication();
    this.objectMapper = server.getObjectMapper();
    this.imageVersionService = server.getImageVersionsService();
  }

  @Override
  protected void handleGet(HttpServletRequest req, HttpServletResponse resp, Session session)
      throws ServletException, IOException {
    /* Get specific record */
    try {
      String response = null;
      if(GET_IMAGE_VERSION_URI.equals(req.getRequestURI())) {
        // imageType must present. If not present throws ServletException
        String imageType = HttpRequestUtils.getParam(req, ImageMgmtConstants.IMAGE_TYPE);
        // imageVersion is optional. Hence can be null
        String imageVersion = HttpRequestUtils.getParam(req, ImageMgmtConstants.IMAGE_VERSION,
            null);
        // create RequestContext DTO to transfer the input request
        RequestContext requestContext = RequestContext.newBuilder()
            .addParam(ImageMgmtConstants.IMAGE_TYPE, imageType) // mandatory parameter
            .addParamIfPresent(ImageMgmtConstants.IMAGE_VERSION, imageVersion) // optional parameter
            .build();
        // invoke service method and get response in string format
        response =
            objectMapper.writeValueAsString(
                imageVersionService.getImageVersion(requestContext));
      }
      this.writeResponse(resp, response);
    } catch(final Exception e) {
      log.error("Requested image metadata not found " + e);
      sendErrorResponse(resp, HttpServletResponse.SC_NOT_FOUND,
          "Requested image metadata not found. Reason : "+e.getMessage());
    }
  }

  @Override
  protected void handlePost(HttpServletRequest req, HttpServletResponse resp, Session session)
      throws ServletException, IOException {
    try {
      String jsonPayload = HttpRequestUtils.getBody(req);
      // Build RequestContext DTO to transfer the input request
      RequestContext requestContext = RequestContext.newBuilder()
          .jsonPayload(jsonPayload)
          .user(session.getUser().getUserId())
          .build();
      // Create image version metadata and image version id
      Integer imageVersionId = imageVersionService.createImageVersion(requestContext);
      // prepare to send response
      resp.setStatus(HttpStatus.SC_CREATED);
      resp.setHeader("Location", CREATE_IMAGE_VERSION_URI_TEMPLATE.createURI(imageVersionId.toString()));
      sendResponse(resp, HttpServletResponse.SC_CREATED, new HashMap<>());
    } catch (final ImageMgmtValidationException e) {
      log.error("Input for creating image version metadata is invalid", e);
      sendErrorResponse(resp, HttpServletResponse.SC_BAD_REQUEST,
          "Bad request for creating image version metadata");
    } catch (final Exception e) {
      log.error("Exception while creating image version metadata", e);
      sendErrorResponse(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Exception while creating image version metadata. "+e.getMessage());
    }
  }
}
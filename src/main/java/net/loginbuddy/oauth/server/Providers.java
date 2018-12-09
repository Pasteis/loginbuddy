/*
 * Copyright (c) 2018. . All rights reserved.
 *
 * This software may be modified and distributed under the terms of the Apache License 2.0 license.
 * See http://www.apache.org/licenses/LICENSE-2.0 for details.
 *
 */

package net.loginbuddy.oauth.server;

import net.loginbuddy.cache.LoginbuddyCache;
import net.loginbuddy.config.Constants;
import net.loginbuddy.config.LoginbuddyConfig;
import net.loginbuddy.oauth.util.Pkce;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@WebServlet(name = "Providers")
public class Providers extends HttpServlet {

    private static Logger LOGGER = Logger.getLogger(String.valueOf(Providers.class));

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doGet(request, response);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        String clientState = request.getParameter(Constants.STATE.getKey());
        String clientRedirectUri = request.getParameter(Constants.REDIRECT_URI.getKey());
        String clientProvider = request.getParameter(Constants.PROVIDER.getKey());
        String clientCodeChallenge = request.getParameter(Constants.CODE_CHALLENGE.getKey());
        String clientCodeChallengeMethod = request.getParameter(Constants.CODE_CHALLENGE_METHOD.getKey());

        if (clientRedirectUri == null || clientRedirectUri.trim().length() == 0 || request.getParameterValues(Constants.REDIRECT_URI.getKey()).length > 1) {
            response.sendError(400, "Missing or invalid redirect_uri parameter");
            return;
        }

        try {
            if (LoginbuddyConfig.getInstance().getConfigUtil().getClientConfigByRedirectUri(clientRedirectUri) == null) {
                response.sendError(400, "The given redirect_uri is unknown or invalid");
                return;
            }
        } catch (Exception e) {
            // should never occur
            LOGGER.severe("The system has not been configured yet!");
            response.sendError(500, "The system has not been configured yet!");
            return;
        }

        if (clientState == null || clientState.trim().length() == 0 || request.getParameterValues(Constants.STATE.getKey()).length > 1) {
            if (clientRedirectUri.contains("?")) {
                clientRedirectUri = clientRedirectUri.concat("&");

            } else {
                clientRedirectUri = clientRedirectUri.concat("?");
            }
            response.sendRedirect(clientRedirectUri.concat("error=invalid_request&error_description=missing+or+invalid+state+parameter"));
            return;
        }

        if (clientProvider != null && request.getParameterValues("provider").length > 1) {
            if (clientRedirectUri.contains("?")) {
                clientRedirectUri = clientRedirectUri.concat("&");

            } else {
                clientRedirectUri = clientRedirectUri.concat("?");
            }
            response.sendRedirect(clientRedirectUri.concat("state=").concat(clientState).concat("&error=invalid_request&error_description=invalid+provider+parameter"));
            return;
        }

        if (clientProvider == null || clientProvider.trim().length() == 0) {
            clientProvider = "";
        }

        if (clientCodeChallenge != null && (request.getParameterValues(Constants.CODE_CHALLENGE.getKey()).length > 1 || !Pkce.verifyChallenge(clientCodeChallenge))) {
            if (clientRedirectUri.contains("?")) {
                clientRedirectUri = clientRedirectUri.concat("&");

            } else {
                clientRedirectUri = clientRedirectUri.concat("?");
            }
            response.sendRedirect(clientRedirectUri.concat("state=").concat(clientState).concat("&error=invalid_request&error_description=invalid+code_challenge"));
            return;
        }

        if ( (clientCodeChallengeMethod != null && request.getParameterValues(Constants.CODE_CHALLENGE_METHOD.getKey()).length > 1) || Pkce.CODE_CHALLENGE_METHOD_PLAIN.equals(clientCodeChallengeMethod)) {
            if (clientRedirectUri.contains("?")) {
                clientRedirectUri = clientRedirectUri.concat("&");

            } else {
                clientRedirectUri = clientRedirectUri.concat("?");
            }
            response.sendRedirect(clientRedirectUri.concat("state=").concat(clientState).concat("&error=invalid_request&error_description=invalid+or+unsupported+code_challenge_method+parameter+or+value"));
            return;
        }

        // Set Attributes that were given by the client
        Map<String, Object> sessionValues = new HashMap<>();
        sessionValues.put(Constants.CLIENT_STATE.getKey(), clientState);
        sessionValues.put(Constants.CLIENT_REDIRECT.getKey(), clientRedirectUri);
        sessionValues.put(Constants.CLIENT_PROVIDER.getKey(), clientProvider);
        sessionValues.put(Constants.CLIENT_CODE_CHALLENGE.getKey(), clientCodeChallenge);
        sessionValues.put(Constants.CLIENT_CODE_CHALLENGE_METHOD.getKey(), clientCodeChallengeMethod);

        // Set Attributes that need to be part of the authorization request
        String nonce = UUID.randomUUID().toString();
        String session = UUID.randomUUID().toString();

        // Remember these values as part of our session
        sessionValues.put(Constants.NONCE.getKey(), nonce);
        sessionValues.put(Constants.SESSION.getKey(), session);

        LoginbuddyCache.getInstance().put(session, sessionValues);

        if ("".equals(clientProvider)) {
            request.getRequestDispatcher("/iapis/providers.jsp?session=".concat(session)).forward(request, response);
        } else {
            response.sendRedirect("initialize?session=".concat(session).concat("&provider=").concat(URLEncoder.encode(clientProvider, "UTF-8")));
        }
    }
}
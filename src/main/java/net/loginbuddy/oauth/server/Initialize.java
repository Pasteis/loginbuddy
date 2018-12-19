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
import net.loginbuddy.config.ProviderConfig;
import net.loginbuddy.config.LoginbuddyConfig;
import net.loginbuddy.oauth.util.MsgResponse;
import net.loginbuddy.oauth.util.Pkce;
import net.loginbuddy.oauth.util.PkcePair;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

@WebServlet(name = "Initialize")
public class Initialize extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(String.valueOf(Initialize.class));

    // initiate authorization flow with provider
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doPost(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String session = request.getParameter("session");
        if (session == null || session.trim().length() == 0 || request.getParameterValues("session").length > 1) {
            LOGGER.warning("Missing session, cannot initiate the authorization flow!");
            response.sendError(400, "Missing session, cannot initiate the authorization flow!");
            return;
        }

        Map<String, Object> sessionValues = (Map<String, Object>) LoginbuddyCache.getInstance().get(session);
        if (sessionValues == null || !session.equals(sessionValues.get(Constants.SESSION.getKey()))) {
            LOGGER.warning("The current session is invalid or it has expired!");
            response.sendError(400, "The current session is invalid or it has expired!");
            return;
        }

        String providerSession = ((String) sessionValues.get(Constants.CLIENT_PROVIDER.getKey())).trim();
        if ("".equals(providerSession)) {
            providerSession = request.getParameter(Constants.PROVIDER.getKey());
            if (providerSession != null && request.getParameterValues(Constants.PROVIDER.getKey()).length > 1) {
                LOGGER.warning("Invalid provider parameter");
                response.sendError(400, "Invalid provider parameter");
                return;
            }
            if (providerSession == null || providerSession.trim().length() == 0) {
                LOGGER.warning("No provider has been selected");
                response.sendError(400, "No provider has been selected");
                return;
            }
        }

        ProviderConfig providerConfig = null;
        try {
            providerConfig = LoginbuddyConfig.getInstance().getConfigUtil().getProviderConfigByProvider(providerSession);
            if (providerConfig == null) {
                LOGGER.warning("The given provider is unknown or invalid");
                response.sendError(400, "The given provider is unknown or invalid");
                return;
            }
        } catch (Exception e) {
            // should never occur
            LOGGER.severe("The system has not been configured yet!");
            response.sendError(500, "The system has not been configured yet!");
            return;
        }

        sessionValues.put("clientProvider", providerSession);

        StringBuilder authorizeUrl = new StringBuilder();

        if (providerConfig.getOpenidConfigurationUri() != null) {
            MsgResponse openIdConfig = getAPI(providerConfig.getOpenidConfigurationUri());
            if (openIdConfig != null && openIdConfig.getStatus() == 200) {
                JSONObject msg = null;
                try {
                    msg = (JSONObject) new JSONParser().parse(openIdConfig.getMsg());
                } catch (ParseException e) {
                    // should never happen
                    LOGGER.warning("For some unknown reason the OpenID Configuration could not be parsed as JSON object!");
                    response.sendError(400, "The OpenID Configuration could not be parsed!");
                }
                authorizeUrl.append(msg.get(Constants.AUTHORIZATION_ENDPOINT.getKey()).toString());
                sessionValues.put(Constants.TOKEN_ENDPOINT.getKey(), msg.get(Constants.TOKEN_ENDPOINT.getKey()).toString());
                sessionValues.put(Constants.USERINFO_ENDPOINT.getKey(), msg.get(Constants.USERINFO_ENDPOINT.getKey()).toString());
            } else {
                LOGGER.warning("The OpenID Connect configuration could not be retrieved. Given URL: '" + providerConfig.getOpenidConfigurationUri() + "'");
                response.sendError(400, "The OpenID Connect configuration coud not be retrieved");
                return;
            }
        } else {
            authorizeUrl.append(providerConfig.getAuthorizationEndpoint());
        }

        PkcePair pair = Pkce.create(Pkce.CODE_CHALLENGE_METHOD_S256);
        sessionValues.put(Constants.CODE_VERIFIER.getKey(), pair.getVerifier());

        authorizeUrl.append("?")
                .append(Constants.CLIENT_ID.getKey())
                .append("=").append(providerConfig.getClientId())
                .append("&").append(Constants.RESPONSE_TYPE.getKey())
                .append("=").append(providerConfig.getResponseType())
                .append("&").append(Constants.SCOPE.getKey())
                .append("=").append(URLEncoder.encode(providerConfig.getScope() == null ? Constants.OPENID_SCOPE.getKey() : providerConfig.getScope(), "UTF-8"))
                .append("&").append(Constants.NONCE.getKey())
                .append("=").append(sessionValues.get(Constants.NONCE.getKey()))
                .append("&").append(Constants.REDIRECT_URI.getKey())
                .append("=").append(URLEncoder.encode(providerConfig.getRedirectUri(), "utf-8"))
                .append("&").append("code_challenge=").append(pair.getChallenge()) // won't produce null unless we ask for method=plain
                .append("&").append("code_challenge_method=S256")
                .append("&").append(Constants.STATE.getKey())
                .append("=").append(sessionValues.get(Constants.SESSION.getKey()));

        LoginbuddyCache.getInstance().put((String) sessionValues.get(Constants.SESSION.getKey()), sessionValues);

        response.sendRedirect(authorizeUrl.toString());

    }

    private MsgResponse getAPI(String targetApi) {
        try {
            HttpGet req = new HttpGet(targetApi);
            HttpClient httpClient = HttpClientBuilder.create().build();

            HttpResponse response = httpClient.execute(req);
            return new MsgResponse(response.getHeaders("Content-Type")[0].getValue(), EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            LOGGER.warning("The openid-configuration could not be retrieved. Given URL: '" + targetApi + "'");
            e.printStackTrace();
            return null;
        }
    }
}
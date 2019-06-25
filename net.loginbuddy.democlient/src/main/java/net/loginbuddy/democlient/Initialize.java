package net.loginbuddy.democlient;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.loginbuddy.common.cache.LoginbuddyCache;
import net.loginbuddy.common.config.Constants;

public class Initialize extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    // No validation whatsoever. This is just for demo!

    String clientId = request.getParameter(Constants.CLIENT_ID.getKey());
    String clientState = request.getParameter(Constants.STATE.getKey());
    String clientRedirectUri = request.getParameter(Constants.REDIRECT_URI.getKey());
    String clientProvider = request.getParameter(Constants.PROVIDER.getKey()); // optional

    Map<String, Object> sessionValues = new HashMap<>();
    sessionValues.put(Constants.CLIENT_ID.getKey(), clientId);
    sessionValues.put(Constants.CLIENT_STATE.getKey(), clientState);
    sessionValues.put(Constants.CLIENT_REDIRECT.getKey(), clientRedirectUri);
    sessionValues.put(Constants.CLIENT_PROVIDER.getKey(), clientProvider);

    LoginbuddyCache.getInstance().put(clientState, sessionValues);

    response.sendRedirect(String.format("https://%s/providers?client_id=%s&redirect_uri=%s&state=%s&provider=%s",
        System.getenv("HOSTNAME_LOGINBUDDY"),
        URLEncoder.encode(clientId, "UTF-8"),
        URLEncoder.encode(clientRedirectUri, "UTF-8"),
        URLEncoder.encode(clientState, "UTF-8"),
        URLEncoder.encode(clientProvider == null ? "" : clientProvider, "UTF-8")));
  }
}

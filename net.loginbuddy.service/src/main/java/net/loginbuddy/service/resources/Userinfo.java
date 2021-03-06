package net.loginbuddy.service.resources;

import net.loginbuddy.common.api.HttpHelper;
import net.loginbuddy.common.cache.LoginbuddyCache;
import net.loginbuddy.common.config.Constants;
import net.loginbuddy.common.util.MsgResponse;
import net.loginbuddy.common.util.ParameterValidator;
import net.loginbuddy.common.util.ParameterValidatorResult;
import net.loginbuddy.service.server.Overlord;
import org.json.simple.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class Userinfo extends Overlord {

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    ParameterValidatorResult accessToken = ParameterValidator.getSingleValue(request.getParameterValues("access_token"));
    String authorizationHeader = request.getHeader("Authorization");

    String hint;
    String[] token = HttpHelper.extractAccessToken(accessToken, authorizationHeader).split(".");
    if(token.length == 3) {
      hint = token[2];
    } else {
      hint = accessToken.getValue();
    }

    MsgResponse msg;
    JSONObject apis = (JSONObject)LoginbuddyCache.getInstance().get(hint);
    if(apis == null) {
      msg = new MsgResponse();
      msg.setStatus(400);
      msg.setContentType("application/json");
      msg.setMsg(HttpHelper.getErrorAsJson("invalid_request", "the given token is unknown").toJSONString());
    } else {
      msg = HttpHelper.getAPI(accessToken.getValue(), (String)apis.get(Constants.USERINFO_ENDPOINT.getKey()));
    }

    response.setStatus(msg.getStatus());
    response.setContentType(msg.getContentType());
    response.getWriter().write(msg.getMsg());
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    doGet(req, resp);
  }
}

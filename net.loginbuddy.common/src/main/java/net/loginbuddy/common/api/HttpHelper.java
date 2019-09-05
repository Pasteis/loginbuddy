package net.loginbuddy.common.api;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import net.loginbuddy.common.config.Constants;
import net.loginbuddy.common.util.MsgResponse;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class HttpHelper {

  private static final Logger LOGGER = Logger.getLogger(String.valueOf(HttpHelper.class));

  private static Pattern urlPattern = Pattern.compile("^http[s]?://[a-zA-Z0-9.\\-:/]{1,92}");

  public HttpHelper() {
  }

  public static boolean couldBeAUrl(String url) {
    if (url == null) {
      return false;
    }
    return urlPattern.matcher(url).matches();
  }

  public static JSONObject getErrorAsJson(String error, String errorDescription) {
    if ("".equals(error)) {
      error = "unknown";
    }
    if ("".equals(errorDescription)) {
      errorDescription = "An error without any description, sorry";
    }
    JSONObject result = new JSONObject();
    result.put("error", error);
    result.put("error_description", errorDescription);
    return result;
  }

  public static MsgResponse getAPI(String accessToken, String targetApi) throws IOException {
    HttpGet req = new HttpGet(targetApi);
    HttpClient httpClient = HttpClientBuilder.create().build();
    req.setHeader(Constants.AUTHORIZATION.getKey(), Constants.BEARER.getKey() + accessToken);

    HttpResponse response = httpClient.execute(req);
    return new MsgResponse(getHeader(response, "content-type", "application/json"),
        EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode());
  }

  public static MsgResponse getAPI(String targetApi) throws IOException {
    HttpGet req = new HttpGet(targetApi);
    HttpClient httpClient = HttpClientBuilder.create().build();

    HttpResponse response = httpClient.execute(req);
    return new MsgResponse(getHeader(response, "content-type", "application/json"),
        EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode());
  }

  // TODO check got 'single' header
  private static String getHeader(HttpResponse response, String headerName, String defaultValue) {
    Header[] headers = response.getHeaders(headerName);
    return headers == null ? defaultValue : headers.length != 1 ? defaultValue : headers[0].getValue();
  }

  public static MsgResponse postTokenExchange(String clientId, String clientSecret, String redirectUri, String authCode,
      String tokenEndpoint, String codeVerifier) throws IOException {

    // build POST request
    List<NameValuePair> formParameters = new ArrayList<>();
    formParameters.add(new BasicNameValuePair(Constants.CODE.getKey(), authCode));
    formParameters.add(new BasicNameValuePair(Constants.CLIENT_ID.getKey(), clientId));
    formParameters.add(new BasicNameValuePair(Constants.CLIENT_SECRET.getKey(), clientSecret));
    formParameters.add(new BasicNameValuePair(Constants.REDIRECT_URI.getKey(), redirectUri));
    formParameters.add(new BasicNameValuePair(Constants.GRANT_TYPE.getKey(), Constants.AUTHORIZATION_CODE.getKey()));
    if (codeVerifier != null) {
      formParameters.add(new BasicNameValuePair(Constants.CODE_VERIFIER.getKey(), codeVerifier));
    }

    return postMessage(formParameters, tokenEndpoint, "application/json");
  }

  public static MsgResponse postMessage(List<NameValuePair> formParameters, String targetUrl, String acceptContentType)
      throws IOException {

    HttpPost req = new HttpPost(targetUrl);

    HttpClient httpClient = HttpClientBuilder.create().build();
    req.setEntity(new UrlEncodedFormEntity(formParameters));
    req.addHeader("Accept", acceptContentType);

    HttpResponse response = httpClient.execute(req);
    return new MsgResponse(getHeader(response, "content-type", "application/json"),
        EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode());
  }

  protected static MsgResponse postMessage(JSONObject input, String targetUrl, String acceptContentType) throws IOException {

    StringEntity requestEntity = new StringEntity(input.toJSONString(), "UTF-8");
    requestEntity.setContentType("application/json");
    HttpPost req = new HttpPost(targetUrl);
    HttpClient httpClient = HttpClientBuilder.create().build();
    req.setEntity(requestEntity);
    req.addHeader("Content-Type", "application/json");
    req.addHeader("Accept", acceptContentType);

    HttpResponse response = httpClient.execute(req);
    return new MsgResponse(getHeader(response, "content-type", acceptContentType),
        EntityUtils.toString(response.getEntity()), response.getStatusLine().getStatusCode());
  }

  public static JSONObject retrieveAndRegister(String discoveryUrl, String redirectUri) {
    return retrieveAndRegister(discoveryUrl, redirectUri, false, false);
  }

  public static JSONObject retrieveAndRegister(String discoveryUrl, String redirectUri, boolean updateProvider, boolean updateIssuer) {

    JSONObject errorResp = new JSONObject();
    try {
      MsgResponse oidcConfig = getAPI(discoveryUrl);
      if (oidcConfig.getStatus() == 200) {
        if (oidcConfig.getContentType().startsWith("application/json")) {
          JSONObject doc = (JSONObject) new JSONParser().parse(oidcConfig.getMsg());
          // TODO check for 'code' and 'authorization_code' as supported
          String registerUrl = (String) doc.get("registration_endpoint");
          if (registerUrl == null || registerUrl.trim().length() == 0) {
            throw new IllegalArgumentException("The registration_url is invalid or not provided");
          } else {
            JSONObject registrationMSg = new JSONObject();
            JSONArray redirectUrisArray = new JSONArray();
            redirectUrisArray.add(redirectUri);
            registrationMSg.put("redirect_uris", redirectUrisArray);
            registrationMSg.put("token_endpoint_auth_method", "client_secret_post");
            MsgResponse registrationResponse = postMessage(registrationMSg, registerUrl, "application/json");
            if (registrationResponse.getStatus() == 200) {
              if (registrationResponse.getContentType().startsWith("application/json")) {
                return providerTemplate(doc, (JSONObject) new JSONParser().parse(registrationResponse.getMsg()), redirectUri, updateProvider, updateIssuer);
              } else {
                // TODO handle the strange case of not getting JSON as content-type
                return getErrorAsJson("invalid_configuration", "the registration response is not JSON");
              }
            } else {
              // TODO handle the non 200 case for registration responses
              return getErrorAsJson("invalid_request", "the registration failed");
            }
          }
        } else {
          // TODO handle the strange case of not getting JSON as a content-type
          return getErrorAsJson("invalid_configuration", "the openid-configuration response is not JSON");
        }
      } else {
        // TODO handdle non 200 responses
        return getErrorAsJson("invalid_configuration", "the opeid-configuration could not be retrieved");
      }
    } catch (Exception e) {
      // TODO need to handle errors
      e.printStackTrace();
      return getErrorAsJson("invalid_server", "no idea what went wrong");
    }
  }

  /**
   * Mappings attributes so that receiving clients can expect the same details at the same location in the response message
   */
  public static JSONObject normalizeDetails(String provider, JSONObject mappings, JSONObject userinfoRespObject) {
    JSONObject result = new JSONObject();
    try {
      mappings = (mappings == null || mappings.size() == 0) ? (JSONObject) new JSONParser()
          .parse(Constants.MAPPING_OIDC.getKey().replace("asis:provider", "asis:" + provider)) : mappings;
    } catch (ParseException e) {
      // should not occur!
      LOGGER.severe(
          "The default mapping for OpenID Connect claims is invalid! Continuing as if nothing has happened ... .");
    }
    if (userinfoRespObject != null && userinfoRespObject.size() > 0) {
      for (Object nextEntry : mappings.entrySet()) {
        Map.Entry entry = (Entry) nextEntry;
        String mappingKey = (String) entry.getKey();
        String mappingRule = (String) entry.getValue();
        String outputValue = "";
        if (mappingRule.contains("[")) {
          String userinfoClaim = (String) userinfoRespObject.get(mappingRule.substring(0, mappingRule.indexOf("[")));
          int idx = Integer.parseInt(Character.toString(mappingRule.charAt(mappingRule.indexOf("[") + 1)));
          try {
            outputValue = userinfoClaim.split(" ")[idx];
          } catch (Exception e) {
            LOGGER.warning(String
                .format("invalid indexed mapping: 'mappings.%s' --> 'userinfo.%s': invalid index: %s", mappingKey,
                    mappingRule, e.getMessage()));
          }
        } else if (mappingRule.startsWith("asis:")) {
          outputValue = mappingRule.substring(5);
        } else if (mappingRule.trim().length() > 0) {
          Object value = userinfoRespObject.get(mappingRule);
          outputValue = value == null ? "" : String.valueOf(value);
        }
        result.put(mappingKey, outputValue == null ? "" : outputValue);
      }
    }
    return result;
  }

  public static String getErrorForRedirect(String redirectUri, String error, String errorDescription) {
    if ("".equals(errorDescription)) {
      errorDescription = "An error without any description, sorry";
    }
    error = urlEncode(error);
    errorDescription = urlEncode(errorDescription);

    return redirectUri.concat("error=").concat(error).concat("&error_description=").concat(errorDescription);
  }

  public static String stringArrayToString(String[] jsonArray) {
    return stringArrayToString(jsonArray, " ");
  }

  /**
   * Turn ["first","second"] to "first second"
   */
  public static String jsonArrayToString(JSONArray jsonArray) {
    return jsonArray.toJSONString().substring(1, jsonArray.toJSONString().length() - 1).replaceAll("[,\"]{1,5}", " ").trim();
  }

  /**
   * @param separator one of [,; ] as a separator between strings. Default: [ ]
   */
  public static String stringArrayToString(String[] jsonArray, String separator) {
    String str = Arrays.toString(jsonArray);
    return str.substring(1, str.length() - 1).replace(",", separator.matches("[,; ]") ? separator : " ");
  }

  public static String urlEncode(String input) {
    try {
      return URLEncoder.encode(input, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      // do not expect this to happen. Therefore -- 'severe'
      LOGGER.severe("Encoding to UTF-8 failed");
      return null;
    }
  }

  private static JSONObject providerTemplate(JSONObject oidcConfig, JSONObject registration, String redirectUri, boolean updateProvider, boolean updateIssuer) {
    JSONObject config = new JSONObject();
    config.put("client_id", registration.get(Constants.CLIENT_ID.getKey()));
    config.put("client_secret", registration.get(Constants.CLIENT_SECRET.getKey()));
    config.put("redirect_uri", redirectUri);
    config.put("scope", HttpHelper.jsonArrayToString((JSONArray) oidcConfig.get(Constants.SCOPES_SUPPORTED.getKey())));
    config.put("authorization_endpoint", oidcConfig.get(Constants.AUTHORIZATION_ENDPOINT.getKey()));
    config.put("token_endpoint", oidcConfig.get(Constants.TOKEN_ENDPOINT.getKey()));
    config.put("userinfo_endpoint", oidcConfig.get(Constants.USERINFO_ENDPOINT.getKey()));
    config.put("jwks_uri", oidcConfig.get(Constants.JWKS_URI.getKey()));
    if (updateIssuer) {
      config.put("issuer", oidcConfig.get(Constants.ISSUER.getKey()));
    }
    if (updateProvider) {
      config.put("provider", oidcConfig.get(Constants.ISSUER.getKey()));
    }
    config.put("response_type", "code");
    return config;
  }
}
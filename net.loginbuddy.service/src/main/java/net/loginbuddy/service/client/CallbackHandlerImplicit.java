package net.loginbuddy.service.client;

import net.loginbuddy.common.api.HttpHelper;
import net.loginbuddy.common.config.Constants;
import net.loginbuddy.common.util.*;
import net.loginbuddy.common.util.ParameterValidatorResult.RESULT;
import net.loginbuddy.service.config.LoginbuddyConfig;
import net.loginbuddy.service.config.ProviderConfig;
import net.loginbuddy.service.util.SessionContext;
import org.json.simple.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.logging.Logger;

public class CallbackHandlerImplicit extends Callback implements CallbackHandler {

    private static final Logger LOGGER = Logger.getLogger(String.valueOf(CallbackHandlerImplicit.class));

    @Override
    public void handleCallback(HttpServletRequest request, HttpServletResponse response, SessionContext sessionCtx, ExchangeBean eb, String provider) throws Exception {

// ***************************************************************
// ** If we did not get a valid id_token parameter we are done
// ***************************************************************

        ParameterValidatorResult idTokenResult = ParameterValidator
                .getSingleValue(request.getParameterValues(Constants.ID_TOKEN.getKey()));
        if (!idTokenResult.getResult().equals(RESULT.VALID)) {
            LOGGER.warning("Missing id_token parameter returned from provider!");
            response.sendRedirect(HttpHelper.getErrorForRedirect(sessionCtx.getString(Constants.CLIENT_REDIRECT_VALID.getKey()), "invalid_session", "missing or invalid id_token parameter"));
            return;
        }

        ProviderConfig providerConfig = null;
        if (Constants.ISSUER_HANDLER_LOGINBUDDY.getKey().equalsIgnoreCase(sessionCtx.getString(Constants.ISSUER_HANDLER.getKey()))) {
            providerConfig = LoginbuddyConfig.getInstance().getConfigUtil().getProviderConfigByProvider(provider);
        } else {
            providerConfig = new ProviderConfig();
            // dynamically registered providers are in a separate container and not available here. Get details out of the session
            providerConfig.setClientId(sessionCtx.getString(Constants.PROVIDER_CLIENT_ID.getKey()));
            providerConfig.setRedirectUri(sessionCtx.getString(Constants.PROVIDER_REDIRECT_URI.getKey()));
            providerConfig.setIssuer(provider);
        }

        JSONObject idTokenPayload = null;
        try {
            // the only provider for which this may stay null is 'self-issued'. For that Jwt().validate ... handles an alternative JSON key that contains the JWK
            String jwks = null;
            if(sessionCtx.getString(Constants.JWKS_URI.getKey()) != null) {
                jwks = HttpHelper.getAPI(sessionCtx.getString(Constants.JWKS_URI.getKey())).getMsg();
            }
            idTokenPayload = new Jwt().validateJwt(idTokenResult.getValue(), jwks, providerConfig.getIssuer(), providerConfig.getClientId(), sessionCtx.getString(Constants.CLIENT_NONCE.getKey()));
            eb.setIdTokenPayload(idTokenPayload);
        } catch (Exception e) {
            LOGGER.warning(String.format("No id_token was issued or it was invalid! Details: %s", e.getMessage()));
            throw e;
        }
        eb.setNormalized(Normalizer.normalizeDetails(providerConfig.getMappingsAsJson(), eb.getEbAsJson(), null));

// ***************************************************************
// ** In this flow there is no token response, we'll create ia manually
// ***************************************************************

        JSONObject tokenResponseObject = new JSONObject();
        tokenResponseObject.put(Constants.ID_TOKEN.getKey(), idTokenResult.getValue());
        eb.setTokenResponse(tokenResponseObject);

        returnAuthorizationCode(response, sessionCtx, eb);
    }
}
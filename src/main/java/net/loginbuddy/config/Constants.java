/*
 * Copyright (c) 2018. . All rights reserved.
 *
 * This software may be modified and distributed under the terms of the Apache License 2.0 license.
 * See http://www.apache.org/licenses/LICENSE-2.0 for details.
 *
 */

package net.loginbuddy.config;

public enum Constants {
    ACCESS_TOKEN("access_token"),
    AUTHORIZATION("Authorization"),
    AUTHORIZATION_CODE("authorization_code"),
    AUTHORIZATION_ENDPOINT("authorization_endpoint"),
    TOKEN_ENDPOINT("token_endpoint"),
    USERINFO_ENDPOINT("userinfo_endpoint"),
    BEARER("Bearer "),
    BASIC("Basic "),
    CLIENT_ID("client_id"),
    CLIENT_SECRET("client_secret"),
    CODE("code"),
    GRANT_TYPE("grant_type"),
    CODE_VERIFIER("code_verifier"),
    CODE_CHALLENGE("code_challenge"),
    CODE_CHALLENGE_METHOD("code_challenge_method"),
    ID_TOKEN("id_token"),
    PROVIDER("provider"),
    REDIRECT_URI("redirect_uri"),
    RESPONSE_TYPE("response_type"),
    SCOPE("scope"),
    STATE("state"),
    ERROR("error"),
    SESSION("session"),
    ACTION_EXPECTED("action_expected"),
    ACTION_LOGIN("login"),
    ACTION_AUTHENTICATE("authenticate"),
    ACTION_GRANT("grant"),
    NONCE("nonce"),
    OPENID_SCOPE("openid profile email"),
    CLIENT_STATE("clientState"),
    CLIENT_REDIRECT("clientRedirectUri"),
    CLIENT_CODE_CHALLENGE("clientCodeChallenge"),
    CLIENT_CODE_CHALLENGE_METHOD("clientCodeChallengeMethod"),
    CLIENT_PROVIDER("clientProvider");

    private final String key;

    Constants(String key) {
        this.key = key;
    }

    public String getKey() {
        return this.key;
    }
}

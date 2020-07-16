package com.mk.auth.beans;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenResult {

    private String username;

    private String access_token;

    private String refresh_token;

    private long expires_in;
}

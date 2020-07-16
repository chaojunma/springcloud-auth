package com.mk.auth.beans;

import lombok.Data;
import javax.validation.constraints.NotEmpty;

@Data
public class TokenRequest {

    @NotEmpty
    private String username;

    @NotEmpty
    private String password;
}

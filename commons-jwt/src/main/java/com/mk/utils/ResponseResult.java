package com.mk.utils;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ResponseResult {

    private int code = 0;

    private String msg;

    private Object data;
}

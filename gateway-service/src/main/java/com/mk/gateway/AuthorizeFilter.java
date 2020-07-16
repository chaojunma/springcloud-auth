package com.mk.gateway;

import com.alibaba.fastjson.JSON;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.mk.enums.ResponseCodeEnum;
import com.mk.exception.TokenAuthenticationException;
import com.mk.utils.JWTUtil;
import com.mk.utils.MD5Util;
import com.mk.utils.ResponseResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {

    @Value("${secretKey:!@#$%^&*}")
    private String secretKey;

    @Value("${issuser:mark}")
    private String issuser;

    @Autowired
    private RedisTemplate redisTemplate;

    private static final String TOKEN_CACHE_PREFIX = "auth-service:";


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest serverHttpRequest = exchange.getRequest();
        ServerHttpResponse serverHttpResponse = exchange.getResponse();

        String token = serverHttpRequest.getHeaders().getFirst("token");

        if (StringUtils.isBlank(token)) {
            serverHttpResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.TOKEN_MISSION);
        }

        // 检查是否可以解密
        String username = null;
        try {
            username = JWTUtil.getUserInfo(token);
        } catch (JWTDecodeException e) {
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.TOKEN_INVALID);
        }

        if (StringUtils.isEmpty(username)) {
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.TOKEN_INVALID);
        }

        // 检查Redis中是否有此Token
        String key = MD5Util.getMD5Str(username);
        if (!redisTemplate.opsForHash().hasKey(TOKEN_CACHE_PREFIX + key, "token")) {
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.TOKEN_INVALID);
        }

        try {
            JWTUtil.verifyToken(issuser, token, secretKey);
        } catch (TokenAuthenticationException ex) {
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.TOKEN_INVALID);
        } catch (Exception ex) {
            return getVoidMono(serverHttpResponse, ResponseCodeEnum.UNKNOWN_ERROR);
        }

        return chain.filter(exchange);
    }


    private Mono<Void> getVoidMono(ServerHttpResponse serverHttpResponse, ResponseCodeEnum responseCodeEnum) {
        serverHttpResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ResponseResult responseResult = ResponseResult.builder()
                .code(responseCodeEnum.getCode())
                .msg(responseCodeEnum.getMessage())
                .build();
        DataBuffer dataBuffer = serverHttpResponse.bufferFactory().wrap(JSON.toJSONString(responseResult).getBytes());
        return serverHttpResponse.writeWith(Flux.just(dataBuffer));
    }


    @Override
    public int getOrder() {
        return 0;
    }
}

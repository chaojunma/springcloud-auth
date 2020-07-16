package com.mk.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mk.auth.beans.RefreshRequest;
import com.mk.auth.beans.TokenRequest;
import com.mk.auth.beans.TokenResult;
import com.mk.auth.entity.User;
import com.mk.auth.service.UserService;
import com.mk.enums.ResponseCodeEnum;
import com.mk.utils.JWTUtil;
import com.mk.utils.MD5Util;
import com.mk.utils.ResponseResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@RestController
@RequestMapping("/oauth")
public class OauthController {


    @Value("${secretKey:!@#$%^&*}")
    private String secretKey;

    @Value("${issuser:mark}")
    private String issuser;

    @Value("${tokenExpireTime:30}")
    private long tokenExpireTime;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private UserService userService;

    private static final String TOKEN_CACHE_PREFIX = "auth-service:";


    /**
     * 获取token
     * @param request
     * @param bindingResult
     * @return
     */
    @PostMapping("/getToken")
    public ResponseResult getToken(@RequestBody @Validated TokenRequest request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseResult
                    .builder()
                    .code(ResponseCodeEnum.PARAMETER_ILLEGAL.getCode())
                    .msg(ResponseCodeEnum.PARAMETER_ILLEGAL.getMessage())
                    .build();
        }

        LambdaQueryWrapper<User> wrapper = new QueryWrapper<User>().lambda();
        wrapper.eq(User::getUsername, request.getUsername());
        wrapper.eq(User::getPassword, request.getPassword());
        User user = userService.getOne(wrapper);

        if (user != null) {

            //  生成Token
            String token = JWTUtil.generateToken(issuser, user.getUsername(), secretKey, tokenExpireTime * 1000);

            //  生成刷新Token
            String refreshToken = UUID.randomUUID().toString().replace("-", "");

            //  放入缓存
            String key = MD5Util.getMD5Str(user.getUsername());
            HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
            hashOperations.put(TOKEN_CACHE_PREFIX + key, "token", token);
            hashOperations.put(TOKEN_CACHE_PREFIX + key, "refreshToken", refreshToken);
            redisTemplate.expire(TOKEN_CACHE_PREFIX + key, tokenExpireTime, TimeUnit.SECONDS);

            TokenResult data = TokenResult.builder()
                    .access_token(token)
                    .refresh_token(refreshToken)
                    .username(user.getUsername())
                    .expires_in(tokenExpireTime)
                    .build();

            return ResponseResult
                    .builder()
                    .code(ResponseCodeEnum.SUCCESS.getCode())
                    .msg(ResponseCodeEnum.SUCCESS.getMessage())
                    .data(data)
                    .build();
        }

        return ResponseResult
                .builder()
                .code(ResponseCodeEnum.LOGIN_ERROR.getCode())
                .msg(ResponseCodeEnum.LOGIN_ERROR.getMessage())
                .build();
    }

    /**
     * 删除token
     * @param username
     * @return
     */
    @GetMapping("/delToken")
    public ResponseResult deltoken(@RequestParam("username") String username) {
        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        String key = MD5Util.getMD5Str(username);
        hashOperations.delete(TOKEN_CACHE_PREFIX + key);

        return ResponseResult
                .builder()
                .code(ResponseCodeEnum.SUCCESS.getCode())
                .msg(ResponseCodeEnum.SUCCESS.getMessage())
                .build();
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refreshToken")
    public ResponseResult refreshToken(@RequestBody @Validated RefreshRequest request, BindingResult bindingResult) {
        String refreshToken = request.getRefreshToken();
        HashOperations<String, String, String> hashOperations = redisTemplate.opsForHash();
        String key = MD5Util.getMD5Str(request.getUsername());
        String originalRefreshToken = hashOperations.get(TOKEN_CACHE_PREFIX + key, "refreshToken");
        if (StringUtils.isBlank(originalRefreshToken) || !originalRefreshToken.equals(refreshToken)) {
            return ResponseResult
                    .builder()
                    .code(ResponseCodeEnum.REFRESH_TOKEN_INVALID.getCode())
                    .msg(ResponseCodeEnum.REFRESH_TOKEN_INVALID.getMessage())
                    .build();
        }

        //  生成新token
        String newToken = JWTUtil.generateToken(issuser, key, secretKey, tokenExpireTime * 1000);
        hashOperations.put(TOKEN_CACHE_PREFIX + key, "token", newToken);
        redisTemplate.expire(TOKEN_CACHE_PREFIX + key, tokenExpireTime, TimeUnit.SECONDS);

        return ResponseResult
                .builder()
                .code(ResponseCodeEnum.SUCCESS.getCode())
                .msg(ResponseCodeEnum.SUCCESS.getMessage())
                .data(newToken)
                .build();
    }
}

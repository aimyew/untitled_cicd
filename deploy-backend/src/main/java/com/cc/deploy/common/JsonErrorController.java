package com.cc.deploy.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.web.servlet.error.AbstractErrorController;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 统一错误响应：把 Spring 默认错误（包括 ResponseStatusException 触发的 401/403 等）
 * 转成与正常响应一致的 {code, msg} JSON 格式，前端 axios 拦截器可统一处理。
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class JsonErrorController extends AbstractErrorController {

    public JsonErrorController(ErrorAttributes errorAttributes) {
        super(errorAttributes);
    }

    @RequestMapping
    public ResponseEntity<BaseResponse<Void>> error(HttpServletRequest request) {
        Map<String, Object> attrs = getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = getStatus(request).value();
        Object msg = attrs.getOrDefault("message", "请求失败");
        return ResponseEntity.status(status).body(BaseResponse.fail(String.valueOf(msg)));
    }
}

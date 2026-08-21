package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增服务器请求
 */
@Data
public class CreateServerRequest {

    @NotBlank(message = "服务器名称不能为空")
    private String name;

    @NotBlank(message = "host 不能为空")
    private String host;

    /** SSH 端口，默认 22 */
    private Integer port;

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 备注 */
    private String remark;
}

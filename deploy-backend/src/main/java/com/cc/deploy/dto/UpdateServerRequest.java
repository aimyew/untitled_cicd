package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 编辑服务器请求
 * <p>密码字段留空表示不修改密码
 */
@Data
public class UpdateServerRequest {

    @NotBlank(message = "服务器名称不能为空")
    private String name;

    @NotBlank(message = "host 不能为空")
    private String host;

    /** SSH 端口，默认 22 */
    private Integer port;

    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码，留空表示不修改 */
    private String password;

    /** 备注 */
    private String remark;
}

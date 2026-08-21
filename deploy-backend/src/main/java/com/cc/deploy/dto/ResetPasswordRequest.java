package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 重置用户密码请求
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}

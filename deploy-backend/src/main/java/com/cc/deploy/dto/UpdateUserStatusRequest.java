package com.cc.deploy.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新用户状态请求
 */
@Data
public class UpdateUserStatusRequest {

    @NotBlank(message = "状态不能为空")
    private String status;
}

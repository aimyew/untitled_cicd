package com.cc.deploy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新用户功能权限请求
 */
@Data
public class UpdateUserPermissionsRequest {

    @NotNull(message = "权限码列表不能为空")
    private List<String> codes;
}

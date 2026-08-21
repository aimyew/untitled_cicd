package com.cc.deploy.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新用户项目部署权限请求
 */
@Data
public class UpdateDeployPermissionsRequest {

    @NotNull(message = "项目 ID 列表不能为空")
    private List<Long> projectIds;
}

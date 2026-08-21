package com.cc.deploy.dto;

import lombok.Data;

/**
 * 更新用户昵称请求
 */
@Data
public class UpdateUserNicknameRequest {

    /** 昵称，可空 */
    private String nickname;
}

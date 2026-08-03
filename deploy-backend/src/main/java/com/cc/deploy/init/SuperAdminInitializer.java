package com.cc.deploy.init;

import com.cc.deploy.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后：如果不存在超管账号，自动生成一个随机密码并打印到控制台。
 * 只在第一次启动时打印，后续重启不再提示（超管应记住或保存好密码）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements ApplicationRunner {

    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        String randomPwd = userService.ensureSuperAdmin();
        if (randomPwd != null) {
            log.warn("=============================================================");
            log.warn("  超管账号已自动创建（首次启动）");
            log.warn("  IP       : 10.10.12.5");
            log.warn("  初始密码 : {}", randomPwd);
            log.warn("  请立即登录后修改密码！此密码仅显示一次");
            log.warn("=============================================================");
        } else {
            log.info("超管账号已存在，跳过初始化");
        }
    }
}

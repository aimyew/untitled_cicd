package com.cc.deploy.service;

import com.cc.deploy.config.DeployProperties;
import com.cc.deploy.entity.DeployRecord;
import com.cc.deploy.entity.Project;
import com.cc.deploy.entity.ServerInfo;
import com.cc.deploy.mapper.DeployRecordMapper;
import com.cc.deploy.mapper.ProjectMapper;
import com.cc.deploy.mapper.ServerInfoMapper;
import com.cc.deploy.util.AesUtil;
import com.cc.deploy.websocket.LogWebSocketHandler;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部署调度：不同项目并行执行，同一项目加锁防重复触发
 */
@Service
@RequiredArgsConstructor
public class DeployService {

    private final ProjectMapper projectMapper;
    private final ServerInfoMapper serverInfoMapper;
    private final DeployRecordMapper recordMapper;
    private final LogWebSocketHandler logHandler;
    private final DeployProperties props;

    /** 最多同时部署 4 个项目 */
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    /** 正在部署中的项目 id */
    private final Set<Long> runningProjects = ConcurrentHashMap.newKeySet();
    /** recordId -> DeployTask 映射，用于取消部署 */
    private final Map<Long, DeployTask> runningTasks = new ConcurrentHashMap<>();

    /**
     * 触发一次部署
     *
     * @return 部署记录 id（前端凭它连 WebSocket 看实时日志）
     */
    public Long deploy(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        Assert.notNull(project, "项目不存在");
        if (!runningProjects.add(projectId)) {
            throw new IllegalArgumentException("项目 [" + project.getName() + "] 正在部署中，请勿重复触发");
        }
        try {
            ServerInfo server = serverInfoMapper.selectById(project.getServerId());
            Assert.notNull(server, "项目关联的服务器不存在，请检查项目配置");
            String password = AesUtil.decrypt(server.getPassword(), props.getAesKey());

            DeployRecord record = new DeployRecord();
            record.setProjectId(project.getId());
            record.setProjectName(project.getName());
            record.setStatus(DeployRecord.STATUS_RUNNING);
            record.setStartTime(LocalDateTime.now());
            recordMapper.insert(record);

            logHandler.open(record.getId());
            DeployTask task = new DeployTask(project, server, password, record, recordMapper,
                    logHandler, props, () -> {
                        runningProjects.remove(projectId);
                        runningTasks.remove(record.getId());
                    });
            runningTasks.put(record.getId(), task);
            pool.submit(task);
            return record.getId();
        } catch (Exception e) {
            runningProjects.remove(projectId);
            throw e;
        }
    }

    public boolean isRunning(Long projectId) {
        return runningProjects.contains(projectId);
    }

    /**
     * 取消部署
     *
     * @return true=成功取消，false=无法取消（已在远程阶段或任务不存在）
     */
    public boolean cancel(Long recordId) {
        DeployTask task = runningTasks.get(recordId);
        if (task == null) {
            return false;
        }
        return task.cancel();
    }

    /**
     * 取每个项目 id 最近一次部署记录（按 id desc 取最新，不含日志大字段）
     */
    public Map<Long, DeployRecord> lastRecordByProjectIds(Collection<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = projectIds.stream().distinct().collect(Collectors.toList());
        // SQL 形如 SELECT ... FROM cc_deploy_record WHERE id IN (
        //   SELECT MAX(id) FROM cc_deploy_record WHERE project_id IN (...) GROUP BY project_id
        // )
        String inSql = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        String subSql = "SELECT MAX(id) FROM cc_deploy_record WHERE project_id IN (" + inSql + ") GROUP BY project_id";
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeployRecord> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DeployRecord>()
                        .select(DeployRecord::getId, DeployRecord::getProjectId, DeployRecord::getProjectName,
                                DeployRecord::getStatus, DeployRecord::getCurrentStep,
                                DeployRecord::getStartTime, DeployRecord::getEndTime)
                        .inSql(DeployRecord::getId, subSql);
        return recordMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(DeployRecord::getProjectId, Function.identity(), (a, b) -> a.getId() > b.getId() ? a : b));
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdownNow();
    }
}

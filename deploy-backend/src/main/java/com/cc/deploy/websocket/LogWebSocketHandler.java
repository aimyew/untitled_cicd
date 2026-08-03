package com.cc.deploy.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 部署实时日志 WebSocket：/ws/log/{recordId}
 * <p>前端连接后先收到已产生的历史日志，之后逐行接收新日志；
 * 结束时收到 "__DEPLOY_FINISHED__:SUCCESS/FAILED" 控制消息。
 */
@Slf4j
@Component
public class LogWebSocketHandler extends TextWebSocketHandler {

    public static final String FINISH_PREFIX = "__DEPLOY_FINISHED__:";

    /** recordId -> 该次部署的所有连接 */
    private final Map<Long, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    /** recordId -> 运行中任务的日志缓冲（供中途加入的连接补发历史） */
    private final Map<Long, StringBuilder> buffers = new ConcurrentHashMap<>();

    /** 部署任务开始时调用 */
    public void open(Long recordId) {
        buffers.put(recordId, new StringBuilder());
    }

    /** 推送一行日志 */
    public void pushLine(Long recordId, String line) {
        StringBuilder buffer = buffers.get(recordId);
        if (buffer != null) {
            synchronized (buffer) {
                buffer.append(line).append('\n');
            }
        }
        broadcast(recordId, line);
    }

    /** 部署结束：发控制消息并清理 */
    public void finish(Long recordId, String status) {
        broadcast(recordId, FINISH_PREFIX + status);
        buffers.remove(recordId);
        List<WebSocketSession> list = sessions.remove(recordId);
        if (list != null) {
            for (WebSocketSession s : list) {
                try {
                    s.close(CloseStatus.NORMAL);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long recordId = parseRecordId(session);
        if (recordId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        sessions.computeIfAbsent(recordId, k -> new CopyOnWriteArrayList<>()).add(session);
        // 补发已产生的日志
        StringBuilder buffer = buffers.get(recordId);
        if (buffer != null) {
            String history;
            synchronized (buffer) {
                history = buffer.toString();
            }
            if (!history.isEmpty()) {
                send(session, history.stripTrailing());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long recordId = parseRecordId(session);
        if (recordId != null) {
            List<WebSocketSession> list = sessions.get(recordId);
            if (list != null) {
                list.remove(session);
            }
        }
    }

    private void broadcast(Long recordId, String message) {
        List<WebSocketSession> list = sessions.get(recordId);
        if (list == null) {
            return;
        }
        for (WebSocketSession s : list) {
            send(s, message);
        }
    }

    private void send(WebSocketSession session, String message) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 发送失败: {}", e.getMessage());
        }
    }

    private Long parseRecordId(WebSocketSession session) {
        try {
            String path = session.getUri().getPath();
            return Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        } catch (Exception e) {
            return null;
        }
    }
}

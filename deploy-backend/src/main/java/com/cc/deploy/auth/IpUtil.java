package com.cc.deploy.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;

/**
 * IP 提取：直连内网场景下优先读 X-Real-IP/X-Forwarded-For，回退到 remoteAddr
 */
public final class IpUtil {

    private IpUtil() {}

    // 合法 IPv4 正则：4 段数字，每段 0-255，段间用点分隔
    private static final Pattern IPV4_PATTERN =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$");

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if (isBlank(ip)) ip = request.getHeader("X-Forwarded-For");
        if (isBlank(ip)) ip = request.getHeader("Proxy-Client-IP");
        if (isBlank(ip)) ip = request.getHeader("WL-Proxy-Client-IP");
        if (isBlank(ip)) ip = request.getRemoteAddr();
        if (ip == null) return "";
        // X-Forwarded-For 多代理时取第一段（客户端真实 IP）
        int comma = ip.indexOf(',');
        if (comma > 0) ip = ip.substring(0, comma).trim();
        // IPv6 本地回环转成 127.0.0.1
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) ip = "127.0.0.1";
        return ip.trim();
    }

    /**
     * IP 格式化：去除所有空白字符后校验是否为合法 IPv4，非法则返回空串
     * <ul>
     *   <li>null / "" / "   " → ""</li>
     *   <li>"127.0.1"          → ""（段数不足）</li>
     *   <li>"256.1.1.1"        → ""（段值越界）</li>
     *   <li>"10.10.12 .2"      → "10.10.12.2"（去除中间空格）</li>
     *   <li>" 192.168.1.1 "    → "192.168.1.1"（去除首尾空白）</li>
     *   <li>"10.10.12.2.3"     → ""（段数过多）</li>
     *   <li>"abc.def.ghi.jkl"  → ""（非数字）</li>
     * </ul>
     */
    public static String formatIp(String ip) {
        if (ip == null || ip.isBlank()) return "";
        // 去除所有空白字符（包括中间的空格、制表符等）
        String cleaned = ip.replaceAll("\\s+", "");
        if (!IPV4_PATTERN.matcher(cleaned).matches()) return "";
        return cleaned;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank() || "unknown".equalsIgnoreCase(s);
    }
}

package com.gduf.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端执行命令请求
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TerminalExecRequestDTO {
    /**
     * 终端会话ID
     */
    private String sessionId;
    /**
     * 执行命令内容
     */
    private String command;
}

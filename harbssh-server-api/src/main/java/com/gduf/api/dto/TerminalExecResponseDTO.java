package com.gduf.api.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端命令响应
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TerminalExecResponseDTO {

    /**
     * 命令输出
     */
    private String output;
}

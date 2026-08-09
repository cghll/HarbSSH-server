package com.gduf.domain.agent.model.entity;

import com.gduf.domain.agent.model.valobj.AiAgentConfigTableVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArmoryCommandEntity {
    private AiAgentConfigTableVO aiAgentConfigTableVO;
}

package com.gduf.domain.agent.model.valobj.intent;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IntentTypeEnumVO {
    // 业务意图：对应一类可被工具链执行的运维动作
    DIAGNOSE("诊断问题"),      // 服务挂了、报错、异常排查
    CONFIGURE("配置修改"),     // 改配置文件、调参数
    DEPLOY("部署操作"),        // 部署、发布、回滚
    MONITOR("监控查看"),       // 看日志、查状态、看资源使用
    SECURITY("安全相关"),      // 防火墙、权限、证书
    BACKUP("备份恢复"),        // 备份数据、恢复数据
    EXECUTE("直接执行"),       // 帮我跑某命令
    EXPLAIN("解释说明"),       // 这个命令什么意思
    SEARCH("搜索查找"),        // 找文件、查进程

    // 控制意图
    COMPOUND("复合指令"),      // 一条消息跨多个业务意图
    CONTINUE("继续"),          // 继续上一个多步任务

    // 兜底意图
    CHAT("闲聊"),
    UNKNOWN("未知");

    private final String label;

}

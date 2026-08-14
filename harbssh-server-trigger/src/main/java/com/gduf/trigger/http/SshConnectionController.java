package com.gduf.trigger.http;


import com.gduf.api.ISshConnectionService;
import com.gduf.api.dto.SshConnectionRequestDTO;
import com.gduf.api.dto.SshConnectionResponseDTO;
import com.gduf.api.response.Response;
import com.gduf.domain.ssh.model.entity.SshConnectionConfigEntity;
import com.gduf.domain.ssh.model.entity.SshConnectionEntity;
import com.gduf.domain.ssh.model.valobj.AuthTypeEnum;
import com.gduf.domain.ssh.service.ISshConnectionDomainService;
import com.gduf.types.enums.ResponseCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;
/**
 * SSH连接管理 HTTP控制器
 *
 * @author harbssh dev
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ssh")
@CrossOrigin(origins = "*")
public class SshConnectionController implements ISshConnectionService {

    private static final java.time.format.DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ISshConnectionDomainService sshConnectionDomainService;

    @PostMapping("/connections")
    @Override
    public Response<SshConnectionResponseDTO> createConnection(@RequestBody SshConnectionRequestDTO requestDTO) {
        try {
            log.info("创建SSH连接 name={} host={}", requestDTO.getConnectionName(), requestDTO.getHost());

            SshConnectionEntity entity = toEntity(requestDTO);
            SshConnectionConfigEntity configEntity = toConfigEntity(requestDTO);

            sshConnectionDomainService.createConnection(entity, configEntity);

            return Response.<SshConnectionResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponseDTO(entity))
                    .build();
        } catch (IllegalArgumentException e) {
            log.warn("创建SSH连接参数错误: {}", e.getMessage());
            return Response.<SshConnectionResponseDTO>builder()
                    .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                    .info(e.getMessage())
                    .build();
        } catch (Exception e) {
            log.error("创建SSH连接失败", e);
            return Response.<SshConnectionResponseDTO>builder()
                    .code(ResponseCode.UN_ERROR.getCode())
                    .info(ResponseCode.UN_ERROR.getInfo())
                    .build();
        }
    }

    @PutMapping("/connections/{connectionId}")
    @Override
    public Response<SshConnectionResponseDTO> updateConnection(@PathVariable String connectionId, @RequestBody SshConnectionRequestDTO requestDTO) {
        try {
            requestDTO.setConnectionId(connectionId);
            SshConnectionEntity entity = toEntity(requestDTO);
            sshConnectionDomainService.updateConnection(entity, toConfigEntity(requestDTO));
            return success(toResponseDTO(sshConnectionDomainService.getConnection(connectionId)));
        } catch (IllegalArgumentException e) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("更新 SSH 连接失败 connectionId={}", connectionId, e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    @DeleteMapping("/connections/{connectionId}")
    @Override
    public Response<Void> deleteConnection(@PathVariable String connectionId) {
        try {
            sshConnectionDomainService.deleteConnection(connectionId);
            return success(null);
        } catch (IllegalArgumentException e) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("删除 SSH 连接失败 connectionId={}", connectionId, e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    @GetMapping("/connections/{connectionId}")
    @Override
    public Response<SshConnectionResponseDTO> getConnection(@PathVariable String connectionId) {
        SshConnectionEntity entity = sshConnectionDomainService.getConnection(connectionId);
        return entity == null ? failure(ResponseCode.ILLEGAL_PARAMETER, "连接不存在") : success(toResponseDTO(entity));
    }

    @GetMapping("/connections")
    @Override
    public Response<List<SshConnectionResponseDTO>> getConnectionList(@RequestParam(defaultValue = "default") String userId) {
        List<SshConnectionResponseDTO> connections = sshConnectionDomainService.getConnectionList(userId).stream()
                .map(this::toResponseDTO)
                .toList();
        return success(connections);
    }

    @PostMapping("/connections/{connectionId}/connect")
    @Override
    public Response<Void> connect(@PathVariable String connectionId) {
        try {
            return sshConnectionDomainService.connect(connectionId) ? success(null) : failure(ResponseCode.UN_ERROR, "SSH 连接失败");
        } catch (IllegalArgumentException e) {
            return failure(ResponseCode.ILLEGAL_PARAMETER, e.getMessage());
        } catch (Exception e) {
            log.error("建立 SSH 连接失败 connectionId={}", connectionId, e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    @PostMapping("/connections/{connectionId}/disconnect")
    @Override
    public Response<Void> disconnect(@PathVariable String connectionId) {
        try {
            sshConnectionDomainService.disconnect(connectionId);
            return success(null);
        } catch (Exception e) {
            log.error("断开 SSH 连接失败 connectionId={}", connectionId, e);
            return failure(ResponseCode.UN_ERROR, ResponseCode.UN_ERROR.getInfo());
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo()).data(data).build();
    }

    private <T> Response<T> failure(ResponseCode code, String info) {
        return Response.<T>builder().code(code.getCode()).info(info).build();
    }


    // ========== DTO <-> Entity 转换 ==========

    private SshConnectionEntity toEntity(SshConnectionRequestDTO dto) {
        return SshConnectionEntity.builder()
                .connectionId(dto.getConnectionId())
                .connectionName(dto.getConnectionName())
                .host(dto.getHost())
                .port(dto.getPort())
                .username(dto.getUsername())
                .authType(dto.getAuthType() != null ? AuthTypeEnum.fromCode(dto.getAuthType()) : AuthTypeEnum.PASSWORD)
                .password(dto.getPassword())
                .privateKey(dto.getPrivateKey())
                .userId(dto.getUserId())
                .build();
    }

    private SshConnectionConfigEntity toConfigEntity(SshConnectionRequestDTO dto) {
        return SshConnectionConfigEntity.builder()
                .connectTimeout(dto.getConnectTimeout())
                .keepaliveInterval(dto.getKeepaliveInterval())
                .startupCommand(dto.getStartupCommand())
                .compression(dto.getCompression())
                .strictHostKeyCheck(dto.getStrictHostKeyCheck())
                .build();
    }

    private SshConnectionResponseDTO toResponseDTO(SshConnectionEntity entity) {
        return SshConnectionResponseDTO.builder()
                .connectionId(entity.getConnectionId())
                .connectionName(entity.getConnectionName())
                .host(entity.getHost())
                .port(entity.getPort())
                .username(entity.getUsername())
                .authType(entity.getAuthType() != null ? entity.getAuthType().getCode() : null)
                .status(entity.getStatus() != null ? entity.getStatus().getCode() : null)
                .encrypted(entity.getEncrypted())
                .userId(entity.getUserId())
                .createdAt(entity.getCreatedAt() != null ? entity.getCreatedAt().format(FMT) : null)
                .updatedAt(entity.getUpdatedAt() != null ? entity.getUpdatedAt().format(FMT) : null)
                .build();
    }

}

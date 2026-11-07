package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.dto.CreateServerRequestDto;
import cz.upce.fei.ems.backend.dto.ServerResponseDto;
import cz.upce.fei.ems.backend.dto.UpdateServerRequestDto;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessageServerService {

    private final MessageServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelRepository channelRepository;
    private final ChannelDeletionService channelDeletionService;

    public List<ServerResponseDto> getServersForUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId())
                .stream()
                .map(MessageServerMember::getServerId)
                .toList();

        return serverRepository.findAllById(serverIds).stream()
                .map(ServerResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public ServerResponseDto createServer(CreateServerRequestDto request, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MessageServer server = new MessageServer();
        server.setName(request.getName());
        server.setType(request.getType());
        MessageServer savedServer = serverRepository.save(server);

        // Creator automatically becomes an ADMIN of the server
        MessageServerMember member = new MessageServerMember();
        member.setServerId(savedServer.getId());
        member.setUserId(user.getId());
        member.setRole(MessageServerRole.ADMINISTRATOR);
        serverMemberRepository.save(member);

        return ServerResponseDto.fromEntity(savedServer);
    }

    public Optional<ServerResponseDto> getServer(Long serverId) {
        return serverRepository.findById(serverId).map(ServerResponseDto::fromEntity);
    }

    @Transactional
    public Optional<ServerResponseDto> updateServer(
            Long serverId, UpdateServerRequestDto request, String username) {
        validateServerAdmin(serverId, username);
        MessageServer server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            return Optional.empty();
        }

        server.setName(request.getName());
        return Optional.of(ServerResponseDto.fromEntity(serverRepository.save(server)));
    }

    public List<ServerResponseDto> getMyServers(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId()).stream()
                .map(MessageServerMember::getServerId)
                .toList();
        return serverRepository.findAllById(serverIds).stream()
                .map(ServerResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public boolean deleteServer(Long serverId, String username) {
        MessageServer server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            return false;
        }

        validateServerAdmin(serverId, username);
        channelRepository.findByServerIdOrderByNameAsc(serverId)
                .forEach(channelDeletionService::deleteChannel);
        serverRepository.delete(server);
        return true;
    }

    public boolean isUserAdmin(Long serverId, String username) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return false;

        return serverMemberRepository.findByServerIdAndUserId(serverId, user.getId())
                .map(member -> member.getRole() == MessageServerRole.ADMINISTRATOR)
                .orElse(false);
    }

    private void validateServerAdmin(Long serverId, String username) {
        if (!isUserAdmin(serverId, username)) {
            throw new SecurityException("Access denied: Only server administrators can perform this action.");
        }
    }
}
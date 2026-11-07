package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.*;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.ChannelRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServerService {

    private final MessageServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final AppUserRepository appUserRepository;
    private final ChannelRepository channelRepository;
    private final ChannelDeletionService channelDeletionService;

    public List<MessageServer> getServersForUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId())
                .stream()
                .map(MessageServerMember::getServerId)
                .toList();

        return serverRepository.findAllById(serverIds);
    }

    @Transactional
    public MessageServer createServer(String serverName, MessageServerType type, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MessageServer server = new MessageServer();
        server.setName(serverName);
        server.setType(type);
        MessageServer savedServer = serverRepository.save(server);

        // Creator automatically becomes an ADMIN of the server
        MessageServerMember member = new MessageServerMember();
        member.setServerId(savedServer.getId());
        member.setUserId(user.getId());
        member.setRole(MessageServerRole.ADMINISTRATOR);
        serverMemberRepository.save(member);

        return savedServer;
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
package cz.upce.fei.ems.backend.service;

import cz.upce.fei.ems.backend.domain.AppUser;
import cz.upce.fei.ems.backend.domain.MessageServer;
import cz.upce.fei.ems.backend.domain.ServerMember;
import cz.upce.fei.ems.backend.domain.ServerRole;
import cz.upce.fei.ems.backend.repository.AppUserRepository;
import cz.upce.fei.ems.backend.repository.MessageServerRepository;
import cz.upce.fei.ems.backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final MessageServerRepository serverRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final AppUserRepository appUserRepository;

    public List<MessageServer> getServersForUser(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Long> serverIds = serverMemberRepository.findByUserId(user.getId())
                .stream()
                .map(ServerMember::getServerId)
                .toList();

        return serverRepository.findAllById(serverIds);
    }

    @Transactional
    public MessageServer createServer(String serverName, cz.upce.fei.ems.backend.domain.ServerType type, String username) {
        AppUser user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MessageServer server = new MessageServer();
        server.setName(serverName);
        server.setType(type);
        MessageServer savedServer = serverRepository.save(server);

        // Creator automatically becomes an ADMIN of the server
        ServerMember member = new ServerMember();
        member.setServerId(savedServer.getId());
        member.setUserId(user.getId());
        member.setRole(ServerRole.ADMINISTRATOR);
        serverMemberRepository.save(member);

        return savedServer;
    }

    @Transactional
    public void deleteServer(Long serverId, String username) {
        validateServerAdmin(serverId, username);
        serverRepository.deleteById(serverId);
    }

    public boolean isUserAdmin(Long serverId, String username) {
        AppUser user = appUserRepository.findByUsername(username).orElse(null);
        if (user == null) return false;

        return serverMemberRepository.findByServerIdAndUserId(serverId, user.getId())
                .map(member -> member.getRole() == ServerRole.ADMINISTRATOR)
                .orElse(false);
    }

    private void validateServerAdmin(Long serverId, String username) {
        if (!isUserAdmin(serverId, username)) {
            throw new SecurityException("Access denied: Only server administrators can perform this action.");
        }
    }
}
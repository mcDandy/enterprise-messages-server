package cz.upce.fei.ems.backend.net.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    // username -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    // channelId -> Set<username>
    private final Map<Long, Set<String>> channelMembers = new ConcurrentHashMap<>();

    public void registerSession(String username, WebSocketSession session) {
        activeSessions.put(username, session);
        session.getAttributes().put("username", username);
    }

    public void unregisterSession(WebSocketSession session) {
        String username = (String) session.getAttributes().get("username");
        if (username != null) {
            activeSessions.remove(username);
            channelMembers.values().forEach(members -> members.remove(username));
        }
    }

    public WebSocketSession getSession(String username) {
        return activeSessions.get(username);
    }

    public boolean isUserOnline(String username) {
        WebSocketSession session = activeSessions.get(username);
        return session != null && session.isOpen();
    }

    public void joinChannel(Long channelId, String username) {
        channelMembers.computeIfAbsent(channelId, k -> ConcurrentHashMap.newKeySet()).add(username);
    }

    public void leaveChannel(Long channelId, String username) {
        Set<String> members = channelMembers.get(channelId);
        if (members != null) {
            members.remove(username);
        }
    }

    public Set<WebSocketSession> getSessionsInChannel(Long channelId) {
        Set<String> usernames = channelMembers.getOrDefault(channelId, Set.of());
        Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

        for (String username : usernames) {
            WebSocketSession session = activeSessions.get(username);
            if (session != null && session.isOpen()) {
                sessions.add(session);
            }
        }
        return sessions;
    }
}
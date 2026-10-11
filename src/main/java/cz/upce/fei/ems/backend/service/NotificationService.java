package cz.upce.fei.ems.backend.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    public void sendNewMessageNotification(String fcmToken, String channelName) {
        if (fcmToken == null || fcmToken.isBlank()) {
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder()
                            .setTitle("New Message")
                            .setBody("You have a new message in channel: " + channelName)
                            .build())
                    .putData("type", "NEW_MESSAGE")
                    .build();

            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            System.err.println("Error sending FCM notification: " + e.getMessage());
        }
    }
}
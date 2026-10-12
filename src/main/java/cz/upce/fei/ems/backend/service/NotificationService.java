package cz.upce.fei.ems.backend.service;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final String ANDROID_CHANNEL_ID = "channel_enterprise_messages";

    public void sendNewMessageNotification(String fcmToken, Long channelId, String channelName) {
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
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .setNotification(AndroidNotification.builder()
                                    .setChannelId(ANDROID_CHANNEL_ID)
                                    .build())
                            .build())
                    .putData("type", "NEW_MESSAGE")
                    .putData("channelId", String.valueOf(channelId))
                    .putData("channelName", channelName)
                    .build();

            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            System.err.println("Error sending FCM notification: " + e.getMessage());
        }
    }
}
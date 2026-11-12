package com.islandride.notifications.service;

import com.islandride.identityaccess.domain.User;
import com.islandride.identityaccess.repository.UserRepository;
import com.islandride.notifications.domain.Notification;
import com.islandride.notifications.domain.NotificationChannel;
import com.islandride.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatchService.class);

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationDispatchService(NotificationRepository notificationRepository,
                                       UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendInAppNotification(Long userId, String title, String body, String targetUrl) {
        if (userId == null) {
            log.debug("Skipping notification dispatch because userId is null.");
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Cannot dispatch notification: User {} not found.", userId);
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title != null ? title.trim() : "IslandRide Update");
        notification.setBody(body != null ? body.trim() : "");
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setTargetUrl(targetUrl);
        notificationRepository.save(notification);
        log.info("Dispatched in-app notification [{}] to user {}", title, userId);
    }
}

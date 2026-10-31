package com.smartleave.service;

import com.smartleave.dto.EmployeeDto.NotificationDto;
import com.smartleave.entity.Notification;
import com.smartleave.entity.User;
import com.smartleave.repository.NotificationRepository;
import com.smartleave.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Creates and lists in-app notifications. */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void notify(Long userId, String message) {
        if (userId == null || message == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user ->
                notificationRepository.save(new Notification(user, message)));
    }

    @Transactional
    public void notify(User user, String message) {
        if (user != null) {
            notificationRepository.save(new Notification(user, message));
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> latest(Long userId) {
        return notificationRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationDto(n.getId(), n.getMessage(), n.isRead(), n.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllRead(userId);
    }
}

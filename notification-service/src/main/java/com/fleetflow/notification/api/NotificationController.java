package com.fleetflow.notification.api;

import com.fleetflow.notification.application.NotificationService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public List<NotificationService.InboxMessage> inbox(@RequestParam UUID customerId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notifications.inbox(customerId, page, size);
    }
}

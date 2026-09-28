package com.hackathon.leave.repo;

import com.hackathon.leave.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** HR inbox: escalations and conflict flags, newest first. */
    List<Notification> findTop50ByTypeInOrderByCreatedAtDesc(Collection<String> types);

    /** Manager inbox: notifications about a set of requests, newest first. */
    List<Notification> findTop50ByRequestIdInOrderByCreatedAtDesc(Collection<Long> requestIds);
}

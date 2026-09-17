package edu.cit.pescante.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findAllByOrderByCreatedAtDesc();
}

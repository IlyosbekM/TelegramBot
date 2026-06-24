package com.qarzbot.repository;

import com.qarzbot.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findBySentFalseAndSendAtBefore(LocalDateTime time);
}

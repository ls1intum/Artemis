package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationOption;

/**
 * Spring Data repository for the NotificationOption entity.
 */
@Repository
public interface NotificationSettingRepository extends JpaRepository<NotificationOption, Long> {

    @Query("""
            SELECT notificationOption
            FROM NotificationOption notificationOption
            LEFT JOIN FETCH notificationOption.user user
            WHERE user.id = :#{#userId}
            """)
    Set<NotificationOption> findAllNotificationOptionsForRecipientWithId(@Param("userId") long userId);
}

package de.tum.cit.aet.artemis.communication.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;

@Repository
public interface EmailNotificationSettingRepository extends JpaRepository<EmailNotificationSetting, Long> {

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
            """)
    List<EmailNotificationSetting> findByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
                AND setting.notificationType = :notificationType
            """)
    Optional<EmailNotificationSetting> findByUserIdAndNotificationType(@Param("userId") Long userId, @Param("notificationType") EmailNotificationType notificationType);
}

package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationSetting;
import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface EmailNotificationSettingRepository extends ArtemisJpaRepository<EmailNotificationSetting, Long> {

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
            """)
    List<EmailNotificationSetting> findByUserId(@Param("userId") long userId);

    @Query("""
            SELECT setting
            FROM EmailNotificationSetting setting
            WHERE setting.user.id = :userId
                AND setting.notificationType = :notificationType
            """)
    Optional<EmailNotificationSetting> findByUserIdAndNotificationType(@Param("userId") long userId, @Param("notificationType") @NotNull EmailNotificationType notificationType);
}

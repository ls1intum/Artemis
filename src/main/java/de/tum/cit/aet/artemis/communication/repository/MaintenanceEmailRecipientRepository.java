package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.dto.MaintenanceEmailRecipientDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Repository for querying maintenance email recipients.
 * Queries the User entity to find instructors of ongoing courses who should receive maintenance notifications.
 */
@Profile(PROFILE_CORE)
@Repository
@Lazy
public interface MaintenanceEmailRecipientRepository extends ArtemisJpaRepository<User, Long> {

    // Note: these two queries share identical WHERE logic; keep them in sync when modifying.
    @Query("""
            SELECT DISTINCT new de.tum.cit.aet.artemis.communication.dto.MaintenanceEmailRecipientDTO(
                u.id, u.email, u.langKey, u.firstName, u.lastName)
            FROM User u
            WHERE u.deleted = FALSE
                AND u.activated = TRUE
                AND u.email IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM Course c
                    WHERE c.instructorGroupName MEMBER OF u.groups
                        AND (c.startDate IS NULL OR c.startDate <= :now)
                        AND (c.endDate IS NULL OR c.endDate >= :now)
                )
                AND NOT EXISTS (
                    SELECT 1 FROM GlobalNotificationSetting s
                    WHERE s.userId = u.id
                        AND s.notificationType = de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType.MAINTENANCE
                        AND s.enabled = FALSE
                )
            """)
    Set<MaintenanceEmailRecipientDTO> findInstructorRecipientsForMaintenanceEmail(@Param("now") ZonedDateTime now);

    @Query("""
            SELECT COUNT(DISTINCT u.id)
            FROM User u
            WHERE u.deleted = FALSE
                AND u.activated = TRUE
                AND u.email IS NOT NULL
                AND EXISTS (
                    SELECT 1 FROM Course c
                    WHERE c.instructorGroupName MEMBER OF u.groups
                        AND (c.startDate IS NULL OR c.startDate <= :now)
                        AND (c.endDate IS NULL OR c.endDate >= :now)
                )
                AND NOT EXISTS (
                    SELECT 1 FROM GlobalNotificationSetting s
                    WHERE s.userId = u.id
                        AND s.notificationType = de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType.MAINTENANCE
                        AND s.enabled = FALSE
                )
            """)
    long countInstructorRecipientsForMaintenanceEmail(@Param("now") ZonedDateTime now);
}

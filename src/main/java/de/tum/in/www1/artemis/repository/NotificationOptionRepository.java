package de.tum.in.www1.artemis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.NotificationOption;

/**
 * Spring Data repository for the NotificationOption entity.
 */
@Repository
public interface NotificationOptionRepository extends JpaRepository<NotificationOption, Long> {

    @Query("""
            SELECT notificationOption FROM NotificationOption notificationOption
            WHERE notificationOption.user.id = :#{#userId}
            """)
    Page<NotificationOption> findAllNotificationOptionsForRecipientWithId(@Param("userId") long userId, Pageable pageable);

    @Query("""
            SELECT notificationOption FROM NotificationOption notificationOption
            WHERE notificationOption.user.id = :#{#userId}
            """)
    NotificationOption[] findAllNotificationOptionsForRecipientWithId(@Param("userId") long userId);

}

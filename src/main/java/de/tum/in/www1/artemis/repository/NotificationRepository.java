package de.tum.in.www1.artemis.repository;

import de.tum.in.www1.artemis.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data  repository for the Notification entity.
 */
@SuppressWarnings("unused")
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select notification from Notification notification where notification.author.login = ?#{principal.username}")
    List<Notification> findByAuthorIsCurrentUser();

}

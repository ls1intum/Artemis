package de.tum.in.www1.artemis.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfigurationId;

@Repository
public interface PushNotificationDeviceConfigurationRepository extends JpaRepository<PushNotificationDeviceConfiguration, PushNotificationDeviceConfigurationId> {

    @Query("SELECT * FROM pushNotificationDeviceConfiguration WHERE expiration_date < now() AND user IN #{#userList}")
    List<PushNotificationDeviceConfiguration> findByUserIn(@Param("userList") List<User> userList);

}

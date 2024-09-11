package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceType;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.domain.User;

/**
 * The Repository used for PushNotificationDeviceConfiguration
 */
@Profile(PROFILE_CORE)
@Repository
public interface PushNotificationDeviceConfigurationRepository extends ArtemisJpaRepository<PushNotificationDeviceConfiguration, PushNotificationDeviceConfigurationId> {

    /**
     * @param users      a list of users you want the deviceTokens for.
     * @param deviceType the device type you want the deviceTokens to be found for. Either Firebase or APNS.
     * @return Finds all the deviceTokens for a specific deviceType for a list of users.
     */
    @Query("""
            SELECT p FROM PushNotificationDeviceConfiguration p
            WHERE p.expirationDate > CURRENT_TIMESTAMP()
                AND p.owner IN :users
                AND p.deviceType = :deviceType
            """)
    List<PushNotificationDeviceConfiguration> findByUserIn(@Param("users") Set<User> users, @Param("deviceType") PushNotificationDeviceType deviceType);

    /**
     * Cleans up the old/expired push notifications device configurations
     */
    @Transactional // ok because of delete
    @Modifying
    @Query("""
            DELETE FROM PushNotificationDeviceConfiguration p
            WHERE p.expirationDate <= CURRENT_TIMESTAMP()
            """)
    void deleteExpiredDeviceConfigurations();
}

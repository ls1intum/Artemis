package de.tum.in.www1.artemis.repository;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.MessagesPreferencesSetting;

/**
 * Spring Data repository for the MessagesPreferencesSetting entity.
 */
@Repository
public interface MessagesPreferencesSettingRepository extends JpaRepository<MessagesPreferencesSetting, Long> {

    @Query("""
            SELECT messagesPreferencesSetting
            FROM MessagesPreferencesSetting messagesPreferencesSetting
            LEFT JOIN FETCH messagesPreferencesSetting.user user
            WHERE user.id = :#{#userId}
            """)
    Set<MessagesPreferencesSetting> findAllMessagesPreferencesSettingsForRecipientWithId(@Param("userId") long userId);
}

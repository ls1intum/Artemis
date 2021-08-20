package de.tum.in.www1.artemis.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.UserOption;

/**
 * Spring Data repository for the Notification entity.
 */
@Repository
public interface UserOptionRepository extends JpaRepository<UserOption, Long> {

    /*
     * @Query(""" SELECT userOption FROM UserOption userOption WHERE userOption.user = :#{#userId} AND userOption.category = "Notification Settings" """) Page<UserOption>
     * findAllUserOptionsForRecipientWithId(@Param("userId") long userId, Pageable pageable);
     */
    @Query("""
            SELECT userOption FROM UserOption userOption
            WHERE userOption.user.id = :#{#userId}
            """)
    Page<UserOption> findAllUserOptionsForRecipientWithId(@Param("userId") long userId, Pageable pageable);
    /*
     * @Query(""" """) void saveAllNotificationOptionsForRecipientWithId(@Param("userId") long userId, @Param("options") NotificationOption[] options);
     */

    /*
     * @Query(""" INSERT INTO notification_option(CATEGORY, GROUP, DESCRIPTION, WEBAPP, EMAIL, USER_ID) VALUES (#{#category}, #{#group}, #{#description}, #{#webapp}, #{#email},
     * #{#userId}); """) //Page<UserOption> saveNewOption( void saveNewOption(
     * @Param("category") String categroy,
     * @Param("group") String group,
     * @Param("description") String description,
     * @Param("webapp") boolean webapp,
     * @Param("email") boolean email,
     * @Param("userId") long userId, Pageable pageable );
     */

}

package de.tum.in.www1.artemis.repository.iris;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the IrisMessageContent entity.
 */
public interface IrisMessageContentRepository extends JpaRepository<IrisMessageContent, Long> {
}

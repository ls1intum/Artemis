package de.tum.in.www1.artemis.repository.iris;

import java.util.List;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.IrisMessageContent;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisMessageContent entity.
 */
public interface IrisMessageContentRepository extends JpaRepository<IrisMessageContent, Long> {

    List<IrisMessageContent> findAllByMessageId(Long messageId);

    @NotNull
    default IrisMessageContent findByIdElseThrow(long messageContentId) throws EntityNotFoundException {
        return findById(messageContentId).orElseThrow(() -> new EntityNotFoundException("Iris Message Content", messageContentId));
    }
}

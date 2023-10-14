package de.tum.in.www1.artemis.repository.iris;

import javax.validation.constraints.NotNull;

import org.springframework.data.jpa.repository.JpaRepository;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data repository for the IrisMessageContent entity.
 */
public interface IrisMessageContentRepository extends JpaRepository<IrisMessageContent, Long> {

    @NotNull
    default IrisMessageContent findByIdElseThrow(long contentId) throws EntityNotFoundException {
        return findById(contentId).orElseThrow(() -> new EntityNotFoundException("Iris Message Content", contentId));
    }

}

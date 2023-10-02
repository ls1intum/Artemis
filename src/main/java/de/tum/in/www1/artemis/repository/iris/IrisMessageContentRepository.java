package de.tum.in.www1.artemis.repository.iris;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessageContent;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import javax.validation.constraints.NotNull;

/**
 * Spring Data repository for the IrisMessageContent entity.
 */
public interface IrisMessageContentRepository extends JpaRepository<IrisMessageContent, Long> {
    
    @NotNull
    default IrisMessageContent findByIdElseThrow(long contentId) throws EntityNotFoundException {
        return findById(contentId).orElseThrow(() -> new EntityNotFoundException("Iris Message Content", contentId));
    }
    
}

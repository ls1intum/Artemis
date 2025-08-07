package de.tum.cit.aet.artemis.nebula.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyResponse;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponse;

@Conditional(NebulaEnabled.class)
@Lazy
@Service
public class FaqProcessingService {

    private static final Logger log = LoggerFactory.getLogger(FaqProcessingService.class);

    private final NebulaConnectionService nebulaConnectionService;

    private final FaqRepository faqRepository;

    public FaqProcessingService(NebulaConnectionService nebulaConnectionService, FaqRepository faqRepository) {
        this.nebulaConnectionService = nebulaConnectionService;
        this.faqRepository = faqRepository;
    }

    /**
     * Executes the FAQ rewriting operation by sending a request to the Nebula service.
     *
     * @param user          the user for whom the rewriting is executed
     * @param course        the course for which the FAQs are being rewritten
     * @param toBeRewritten the text that needs to be rewritten
     * @return a response containing the rewritten FAQs
     */
    public FaqRewritingResponse executeRewriting(User user, Course course, String toBeRewritten) {
        List<FaqDTO> faqDTOs = faqRepository.findAllByCourseIdAndFaqState(course.getId(), FaqState.ACCEPTED).stream().map(FaqDTO::new).toList();
        FaqRewritingDTO faqRewritingDTO = new FaqRewritingDTO(toBeRewritten, faqDTOs);
        FaqRewritingResponse response = nebulaConnectionService.executeFaqRewriting(faqRewritingDTO);
        log.info("Rewriting FAQ for user: {} in course: {} with text: {}", user.getLogin(), course.getTitle(), toBeRewritten);
        return response;
    }

    /**
     * Executes the FAQ consistency check operation by sending a request to the Nebula service.
     *
     * @param user        the user for whom the consistency check is executed
     * @param course      the course for which the FAQs are being checked
     * @param toBeChecked the faqAnswer that needs to be checked for consistency
     * @return a response containing the results of the consistency check
     */
    public FaqConsistencyResponse executeConsistencyCheck(User user, Course course, String toBeChecked) {
        List<FaqDTO> faqDTOs = faqRepository.findAllByCourseIdAndFaqState(course.getId(), FaqState.ACCEPTED).stream().map(FaqDTO::new).toList();
        FaqConsistencyDTO faqConsistencyDTO = new FaqConsistencyDTO(toBeChecked, faqDTOs);
        FaqConsistencyResponse response = nebulaConnectionService.executeFaqConsistencyCheck(faqConsistencyDTO);
        log.info("Consistency check FAQ for user: {} in course: {} with text: {}", user.getLogin(), course.getTitle(), toBeChecked);
        return response;
    }
}

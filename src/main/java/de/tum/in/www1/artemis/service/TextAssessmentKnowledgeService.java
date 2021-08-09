package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.TextAssessmentKnowledge;
import de.tum.in.www1.artemis.repository.TextAssesmentKnowledgeRepository;

@Service
public class TextAssessmentKnowledgeService {

    private final Logger log = LoggerFactory.getLogger(TextAssessmentKnowledgeService.class);

    private final AuthorizationCheckService authCheckService;

    private final TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository;

    public TextAssessmentKnowledgeService(TextAssesmentKnowledgeRepository textAssesmentKnowledgeRepository, AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
        this.textAssesmentKnowledgeRepository = textAssesmentKnowledgeRepository;
    }

    // delete only when no exercises use the knowledge

    // create only when we create exercise
    public TextAssessmentKnowledge createNewKnowledge() {
        TextAssessmentKnowledge knowledge = new TextAssessmentKnowledge();
        textAssesmentKnowledgeRepository.save(knowledge);
        return knowledge;
    }

}

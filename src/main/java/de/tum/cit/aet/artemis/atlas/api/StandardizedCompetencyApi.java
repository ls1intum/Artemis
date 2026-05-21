package de.tum.cit.aet.artemis.atlas.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.KnowledgeArea;
import de.tum.cit.aet.artemis.atlas.service.competency.StandardizedCompetencyService;

@Conditional(AtlasEnabled.class)
@Controller
@Lazy
public class StandardizedCompetencyApi extends AbstractAtlasApi {

    private final StandardizedCompetencyService standardizedCompetencyService;

    public StandardizedCompetencyApi(StandardizedCompetencyService standardizedCompetencyService) {
        this.standardizedCompetencyService = standardizedCompetencyService;
    }

    /**
     * Gets all knowledge areas structured as a tree with their competencies.
     *
     * @return the list of root knowledge areas containing all descendants and competencies
     */
    public List<KnowledgeArea> getAllForTreeView() {
        return standardizedCompetencyService.getAllForTreeView();
    }
}

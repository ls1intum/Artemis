package de.tum.in.www1.artemis.service.iris.session;

import java.util.*;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.domain.iris.message.*;
import de.tum.in.www1.artemis.domain.iris.session.IrisCompetencyGenerationSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisSession;
import de.tum.in.www1.artemis.repository.iris.IrisSessionRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.iris.exception.IrisParseResponseException;
import de.tum.in.www1.artemis.service.iris.settings.IrisSettingsService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

@Service
@Profile("iris")
public class IrisCompetencyGenerationSessionService implements IrisSessionSubServiceInterface {

    private final Logger log = LoggerFactory.getLogger(IrisCompetencyGenerationSessionService.class);

    private final IrisConnectorService irisConnectorService;

    private final IrisSettingsService irisSettingsService;

    private final AuthorizationCheckService authCheckService;

    private final IrisSessionRepository irisSessionRepository;

    public IrisCompetencyGenerationSessionService(IrisConnectorService irisConnectorService, IrisSettingsService irisSettingsService, AuthorizationCheckService authCheckService,
            IrisSessionRepository irisSessionRepository) {
        this.irisConnectorService = irisConnectorService;
        this.irisSettingsService = irisSettingsService;
        this.authCheckService = authCheckService;
        this.irisSessionRepository = irisSessionRepository;
    }

    @Override
    public void sendOverWebsocket(IrisMessage message) {
        throw new UnsupportedOperationException("Sending messages over websocket is not supported for competency generation with IRIS.");
    }

    @Override
    public void requestAndHandleResponse(IrisSession irisSession) {
        throw new UnsupportedOperationException("Requesting and handling responses is not supported for competency generation with IRIS.");
    }

    @Override
    public void checkHasAccessToIrisSession(IrisSession irisSession, User user) {
        // var competencyGenerationSession = castToSessionType(irisSession, IrisCompetencyGenerationSession.class);
        // TODO: check if user is instructor in this course
        // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR,TODO:COURSE, user);
    }

    @Override
    public void checkRateLimit(User user) {
        // TODO: maybe implement a rate limit(?)
    }

    @Override
    public void checkIsIrisActivated(IrisSession irisSession) {
        // TODO: would have to check if IRIS is enabled for a course
        // todo: do nothing.
    }

    public List<Competency> generateCompetencyRecommendations(String courseDescription, Course course) {
        var irisSession = new IrisCompetencyGenerationSession();
        // TODO: this does nothing right now.
        checkHasAccessToIrisSession(irisSession, null);
        irisSession = irisSessionRepository.save(irisSession);

        var irisSettings = irisSettingsService.getCombinedIrisSettingsFor(course, false);
        Map<String, Object> parameters = Map.of("courseDescription", courseDescription, "taxonomyOptions", CompetencyTaxonomy.values());

        try {
            var irisMessage = irisConnectorService.sendRequestV2(irisSettings.irisCompetencyGenerationSettings().getTemplate().getContent(),
                    irisSettings.irisCompetencyGenerationSettings().getPreferredModel(), parameters).get();
            // TODO: save as part of session
            return toCompetencies(irisMessage.content());
        }
        catch (InterruptedException | ExecutionException e) {
            log.error("Unable to generate competencies", e);
            throw new InternalServerErrorException("Unable to generate competencies: " + e.getMessage());
        }
    }

    private List<Competency> toCompetencies(JsonNode content) throws IrisParseResponseException {
        List<Competency> competencies = new ArrayList<>();
        for (JsonNode node : content.get("competencies")) {
            try {
                Competency competency = new Competency();
                competency.setTitle(node.required("title").asText());
                competency.setDescription(node.required("description").asText());
                competency.setTaxonomy(CompetencyTaxonomy.valueOf(node.required("taxonomy").asText()));

                competencies.add(competency);
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse Competency: " + node.toPrettyString(), e);
            }
        }
        return competencies;
    }

}

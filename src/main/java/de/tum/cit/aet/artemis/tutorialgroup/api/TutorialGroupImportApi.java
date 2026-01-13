package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupImportService;

/**
 * API for importing tutorial groups.
 */
@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupImportApi extends AbstractTutorialGroupApi {

    private final TutorialGroupImportService tutorialGroupImportService;

    public TutorialGroupImportApi(TutorialGroupImportService tutorialGroupImportService) {
        this.tutorialGroupImportService = tutorialGroupImportService;
    }

    /**
     * Import all tutorial group configurations from the source course to the target course.
     * This copies the tutorial group structure (title, schedule, capacity, etc.) but NOT student registrations.
     *
     * @param sourceCourseId the ID of the course to import from
     * @param targetCourse   the course to import to
     * @param requestingUser the user requesting the import (will be set as default teaching assistant)
     * @return the list of imported tutorial groups
     */
    public List<TutorialGroup> importTutorialGroups(long sourceCourseId, Course targetCourse, User requestingUser) {
        return tutorialGroupImportService.importTutorialGroups(sourceCourseId, targetCourse, requestingUser);
    }
}

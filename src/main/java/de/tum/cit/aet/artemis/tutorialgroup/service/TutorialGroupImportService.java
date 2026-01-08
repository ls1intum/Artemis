package de.tum.cit.aet.artemis.tutorialgroup.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupScheduleRepository;

/**
 * Service for importing tutorial group configurations from one course to another.
 * Note: This imports the tutorial group structure (title, schedule, capacity, etc.)
 * but NOT student registrations.
 */
@Conditional(TutorialGroupEnabled.class)
@Lazy
@Service
public class TutorialGroupImportService {

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupImportService.class);

    private final TutorialGroupRepository tutorialGroupRepository;

    private final TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    public TutorialGroupImportService(TutorialGroupRepository tutorialGroupRepository, TutorialGroupScheduleRepository tutorialGroupScheduleRepository,
            TutorialGroupChannelManagementService tutorialGroupChannelManagementService) {
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.tutorialGroupScheduleRepository = tutorialGroupScheduleRepository;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
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
        log.debug("Importing tutorial groups from course {} to course {}", sourceCourseId, targetCourse.getId());

        Set<TutorialGroup> sourceTutorialGroups = tutorialGroupRepository.findAllByCourseIdWithTeachingAssistantRegistrationsAndSchedule(sourceCourseId);
        List<TutorialGroup> importedGroups = new ArrayList<>();

        for (TutorialGroup source : sourceTutorialGroups) {
            TutorialGroup newGroup = createTutorialGroupCopy(source, targetCourse, requestingUser);
            TutorialGroup savedGroup = tutorialGroupRepository.save(newGroup);

            // Copy schedule if exists
            if (source.getTutorialGroupSchedule() != null) {
                TutorialGroupSchedule newSchedule = copySchedule(source.getTutorialGroupSchedule(), savedGroup);
                tutorialGroupScheduleRepository.save(newSchedule);
                savedGroup.setTutorialGroupSchedule(newSchedule);
            }

            // Create channel for the tutorial group
            tutorialGroupChannelManagementService.createChannelForTutorialGroup(savedGroup);

            importedGroups.add(savedGroup);
        }

        log.debug("Imported {} tutorial groups to course {}", importedGroups.size(), targetCourse.getId());
        return importedGroups;
    }

    /**
     * Creates a copy of the tutorial group with the new course and without registrations.
     *
     * @param source         the source tutorial group
     * @param targetCourse   the target course
     * @param requestingUser the user to set as teaching assistant
     * @return a new tutorial group with copied properties
     */
    private TutorialGroup createTutorialGroupCopy(TutorialGroup source, Course targetCourse, User requestingUser) {
        TutorialGroup newGroup = new TutorialGroup();
        newGroup.setTitle(source.getTitle());
        newGroup.setCampus(source.getCampus());
        newGroup.setLanguage(source.getLanguage());
        newGroup.setCapacity(source.getCapacity());
        newGroup.setIsOnline(source.getIsOnline());
        newGroup.setAdditionalInformation(source.getAdditionalInformation());
        newGroup.setCourse(targetCourse);
        newGroup.setTeachingAssistant(requestingUser);
        // No registrations - empty set
        newGroup.setRegistrations(new HashSet<>());
        return newGroup;
    }

    /**
     * Creates a copy of the tutorial group schedule.
     *
     * @param sourceSchedule   the source schedule
     * @param newTutorialGroup the new tutorial group to attach the schedule to
     * @return a new schedule with copied properties
     */
    private TutorialGroupSchedule copySchedule(TutorialGroupSchedule sourceSchedule, TutorialGroup newTutorialGroup) {
        TutorialGroupSchedule newSchedule = new TutorialGroupSchedule();
        newSchedule.setTutorialGroup(newTutorialGroup);
        newSchedule.setDayOfWeek(sourceSchedule.getDayOfWeek());
        newSchedule.setStartTime(sourceSchedule.getStartTime());
        newSchedule.setEndTime(sourceSchedule.getEndTime());
        newSchedule.setRepetitionFrequency(sourceSchedule.getRepetitionFrequency());
        newSchedule.setValidFromInclusive(sourceSchedule.getValidFromInclusive());
        newSchedule.setValidToInclusive(sourceSchedule.getValidToInclusive());
        newSchedule.setLocation(sourceSchedule.getLocation());
        return newSchedule;
    }
}

package de.tum.in.www1.artemis.lecture.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.lecture.service.messaging.LectureServiceProducer;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing lectures.
 */
@Service
public class LectureService {

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureUnitService lectureUnitService;

    private final LectureServiceProducer lectureServiceProducer;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService, LectureUnitRepository lectureUnitRepository,
                          LectureUnitService lectureUnitService, LectureServiceProducer lectureServiceProducer) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureUnitService = lectureUnitService;
        this.lectureServiceProducer = lectureServiceProducer;
    }

    /**
     * For tutors, admins and instructors returns lecture with all attachments, for students lecture with only active attachments
     *
     * @param lectureWithAttachments lecture that has attachments
     * @param user the user for which this call should filter
     * @return lecture with filtered attachments
     */
    public Lecture filterActiveAttachments(Lecture lectureWithAttachments, User user) {
        Course course = lectureWithAttachments.getCourse();
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return lectureWithAttachments;
        }

        HashSet<Attachment> filteredAttachments = new HashSet<>();
        for (Attachment attachment : lectureWithAttachments.getAttachments()) {
            if (attachment.getReleaseDate() == null || attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
                filteredAttachments.add(attachment);
            }
        }
        lectureWithAttachments.setAttachments(filteredAttachments);
        return lectureWithAttachments;
    }

    /**
     * Filter active attachments for a set of lectures.
     *
     * @param lecturesWithAttachments lectures that have attachments
     * @param user the user for which this call should filter
     * @return lectures with filtered attachments
     */
    public Set<Lecture> filterActiveAttachments(Set<Lecture> lecturesWithAttachments, User user) {
        Set<Lecture> lecturesWithFilteredAttachments = new HashSet<>();
        for (Lecture lecture : lecturesWithAttachments) {
            lecturesWithFilteredAttachments.add(filterActiveAttachments(lecture, user));
        }
        return lecturesWithFilteredAttachments;
    }

    /**
     * Filter the lecture content depending on the user's role including only the content the user is allowed to access.
     *
     * @param lecture the lecture which content will be filtered
     * @param user the user for whom the content will be filtered
     * @return the filtered lecture
     */
    public Lecture filterLectureContentForUser(Lecture lecture, User user) {
        Lecture filteredLecture = filterActiveAttachments(lecture, user);

        // The Objects::nonNull is needed here because the relationship lecture -> lecture units is ordered and
        // hibernate sometimes adds nulls into the list of lecture units to keep the order
        Set<Exercise> relatedExercises = filteredLecture.getLectureUnits().stream().filter(Objects::nonNull).filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
            .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).collect(Collectors.toSet());

        Set<Exercise> exercisesWithAllInformationNeeded = lectureServiceProducer.getLectureExercises(relatedExercises, user);
        List<LectureUnit> lectureUnitsUserIsAllowedToSee = filteredLecture.getLectureUnits().stream().filter(lectureUnit -> {
            if (lectureUnit == null) {
                return false;
            }
            if (lectureUnit instanceof ExerciseUnit) {
                return ((ExerciseUnit) lectureUnit).getExercise() != null && authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)
                    && exercisesWithAllInformationNeeded.contains(((ExerciseUnit) lectureUnit).getExercise());
            }
            else if (lectureUnit instanceof AttachmentUnit) {
                return ((AttachmentUnit) lectureUnit).getAttachment() != null && authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user);
            }
            else {
                return authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user);
            }
        }).peek(lectureUnit -> {
            if (lectureUnit instanceof ExerciseUnit) {
                Exercise exercise = ((ExerciseUnit) lectureUnit).getExercise();
                // we replace the exercise with one that contains all the information needed for correct display
                exercisesWithAllInformationNeeded.stream().filter(exercise::equals).findAny().ifPresent(((ExerciseUnit) lectureUnit)::setExercise);
            }
        }).collect(Collectors.toList());

        filteredLecture.setLectureUnits(lectureUnitsUserIsAllowedToSee);
        return filteredLecture;
    }

    /**
     * Deletes the given lecture.
     * Attachments and Lecture Units are not explicitly deleted, as the delete operation is cascaded by the database.
     * @param lecture the lecture to be deleted
     */
    @Transactional
    public void delete(Lecture lecture) {
        Optional<Lecture> lectureToDeleteOptional = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture.getId());
        if (lectureToDeleteOptional.isEmpty()) {
            return;
        }
        Lecture lectureToDelete = lectureToDeleteOptional.get();
        // Hibernate sometimes adds null into the list of lecture units to keep the order, to prevent a NullPointerException we have to filter them
        List<LectureUnit> lectureUnits = lectureToDelete.getLectureUnits().stream().filter(Objects::nonNull).toList();
        // update associated learning goals
        for (LectureUnit lectureUnit : lectureUnits) {
            lectureUnitRepository.findByIdWithLearningGoalsBidirectional(lectureUnit.getId()).ifPresent(lectureUnitFromDb -> {
                Set<LearningGoal> associatedLearningGoals = new HashSet<>(lectureUnitFromDb.getLearningGoals());
                for (LearningGoal learningGoal : associatedLearningGoals) {
                    lectureUnitService.disconnectLectureUnitAndLearningGoal(lectureUnit, learningGoal);
                }
            });
        }
        lectureRepository.deleteById(lectureToDelete.getId());
    }
}

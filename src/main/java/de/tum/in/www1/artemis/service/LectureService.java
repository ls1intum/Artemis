package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitRepository;

@Service
public class LectureService {

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final LearningGoalRepository learningGoalRepository;

    private final LectureUnitRepository lectureUnitRepository;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService, LearningGoalRepository learningGoalRepository,
            LectureUnitRepository lectureUnitRepository) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
        this.learningGoalRepository = learningGoalRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    public Lecture save(Lecture lecture) {
        return lectureRepository.save(lecture);
    }

    public Optional<Lecture> findByIdWithStudentQuestionsAndLectureModules(Long lectureId) {
        return lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lectureId);
    }

    /**
     * Finds all lectures connected to a course with the associated attachments
     * @param courseId if of the course
     * @return set of lectures connected to the course with associated attachments
     */
    public Set<Lecture> findAllByCourseIdWithAttachments(Long courseId) {
        return lectureRepository.findAllByCourseIdWithAttachments(courseId);
    }

    /**
     * Finds all lectures connected to a course with the associated lecture units and attachments
     * @param courseId id of the course
     * @return set of lectures connected to the course with associated lecture units and attachments
     */
    public Set<Lecture> findAllByCourseIdWithAttachmentsAndLectureUnits(Long courseId) {
        return lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnits(courseId);
    }

    /**
     * For tutors, admins and instructors returns  lecture with all attachments, for students lecture with only active attachments
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
     * Deletes the given lecture.
     * Attachments and Lecture Units are not explicitly deleted, as the delete operation is cascaded by the database.
     * @param lecture the lecture to be deleted
     */
    @Transactional // ok
    public void delete(Lecture lecture) {
        Optional<Lecture> lectureToDeleteOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lecture.getId());
        if (lectureToDeleteOptional.isEmpty()) {
            return;
        }
        Lecture lectureToDelete = lectureToDeleteOptional.get();

        // update associated learning goals
        for (LectureUnit lectureUnit : lectureToDelete.getLectureUnits()) {
            Optional<LectureUnit> lectureUnitFromDbOptional = lectureUnitRepository.findByIdWithLearningGoalsBidirectional(lectureUnit.getId());

            if (lectureUnitFromDbOptional.isPresent()) {
                LectureUnit lectureUnitFromDb = lectureUnitFromDbOptional.get();
                Set<LearningGoal> associatedLearningGoals = new HashSet<>(lectureUnitFromDb.getLearningGoals());
                for (LearningGoal learningGoal : associatedLearningGoals) {
                    Optional<LearningGoal> learningGoalFromDbOptional = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
                    if (learningGoalFromDbOptional.isPresent()) {
                        LearningGoal learningGoalFromDb = learningGoalFromDbOptional.get();
                        learningGoalFromDb.removeLectureUnit(lectureUnitFromDb);
                        learningGoalRepository.save(learningGoalFromDb);
                    }

                }
            }
        }
        Optional<Lecture> lectureToDeleteUpdatedOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lecture.getId());
        if (lectureToDeleteUpdatedOptional.isEmpty()) {
            return;
        }

        lectureRepository.delete(lectureToDeleteUpdatedOptional.get());
    }

}

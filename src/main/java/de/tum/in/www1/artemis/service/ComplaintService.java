package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.assessment.dashboard.ExerciseMapEntry;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * Service for managing complaints.
 */
@Service
public class ComplaintService {

    private static final String ENTITY_NAME = "complaint";

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultRepository resultRepository;

    private final ResultService resultService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    public ComplaintService(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, ResultRepository resultRepository,
            ResultService resultService, CourseRepository courseRepository, ExamRepository examRepository, UserRepository userRepository) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
    }

    /**
     * Create a new complaint by checking if the user is still allowed to submit complaints and in the case of normal course exercises
     * whether the user still enough complaints left.
     *
     * @param complaint the complaint to create
     * @param principal the current Principal
     * @param examId the optional examId. This is only set if the exercise is an exam exercise
     * @return the saved complaint
     */
    public Complaint createComplaint(Complaint complaint, OptionalLong examId, Principal principal) {
        Result originalResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(complaint.getResult().getId())
                .orElseThrow(() -> new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "resultnotfound"));

        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        Participant participant = studentParticipation.getParticipant(); // Team or Student

        Long courseId = studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId();

        // checking if it is allowed to create a complaint
        if (examId.isPresent()) {
            final Exam exam = examRepository.findByIdElseThrow(examId.getAsLong());
            final List<User> instructors = userRepository.getInstructors(exam.getCourse());
            boolean examTestRun = instructors.stream().anyMatch(instructor -> instructor.getLogin().equals(principal.getName()));
            if (!examTestRun && !isTimeOfComplaintValid(exam)) {
                throw new BadRequestAlertException("You cannot submit a complaint after the student review period", ENTITY_NAME, "afterStudentReviewPeriod");
            }
        }
        else {
            // Retrieve course to get Max Complaints, Max Team Complaints and Max Complaint Time
            final Course course = courseRepository.findByIdElseThrow(courseId);

            if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                long numberOfUnacceptedComplaints = countUnacceptedComplaintsByParticipantAndCourseId(participant, courseId);
                long numberOfAllowedComplaintsInCourse = getMaxComplaintsPerParticipant(course, participant);
                if (numberOfUnacceptedComplaints >= numberOfAllowedComplaintsInCourse) {
                    throw new BadRequestAlertException("You cannot have more than " + numberOfAllowedComplaintsInCourse + " open or rejected complaints at the same time.",
                            ENTITY_NAME, "tooManyComplaints");
                }
            }
            else if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK && !course.getRequestMoreFeedbackEnabled()) {
                throw new BadRequestAlertException("You cannot request more feedback in this course because this feature has been disabled by the instructors.", ENTITY_NAME,
                        "moreFeedbackRequestsDisabled");
            }
            validateTimeOfComplaintOrRequestMoreFeedback(originalResult, studentParticipation.getExercise(), course, complaint.getComplaintType());
        }

        if (!studentParticipation.isOwnedBy(principal.getName())) {
            throw new BadRequestAlertException("You can create a complaint only for a result you submitted", ENTITY_NAME, "differentuser");
        }

        originalResult.setHasComplaint(true);
        // When a student complains, a tutor has to manually correct the submission, so it is no longer automatic
        if (originalResult.getAssessmentType() == AssessmentType.AUTOMATIC) {
            originalResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }

        complaint.setSubmittedTime(ZonedDateTime.now());
        complaint.setParticipant(participant);
        complaint.setResult(originalResult);
        resultRepository.save(originalResult);

        return complaintRepository.save(complaint);
    }

    /**
     * Count the number of unaccepted complaints of a student or team in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the
     * number of complaints for a student or team in a course. Requests for more feedback are not counted here.
     *
     * @param participant the participant (student or team)
     * @param courseId  the id of the course
     * @return the number of unaccepted complaints
     */
    public long countUnacceptedComplaintsByParticipantAndCourseId(Participant participant, long courseId) {
        if (participant instanceof User) {
            return complaintRepository.countUnacceptedComplaintsByComplaintTypeStudentIdAndCourseId(participant.getId(), courseId);
        }
        else if (participant instanceof Team) {
            return complaintRepository.countUnacceptedComplaintsByComplaintTypeTeamShortNameAndCourseId(participant.getParticipantIdentifier(), courseId);
        }
        else {
            throw new Error("Unknown participant type");
        }
    }

    public long countComplaintsByCourseId(long courseId) {
        return complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.COMPLAINT);
    }

    public long countMoreFeedbackRequestsByCourseId(long courseId) {
        return complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
    }

    /**
     * Counts the number of responses to complaints for the given course id
     *
     * @param courseId the id of the course
     * @return the number of responses
     */
    public long countComplaintResponsesByCourseId(long courseId) {
        return complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(courseId,
                ComplaintType.COMPLAINT);
    }

    /**
     * Counts the number of responses to feedback requests for the given course id
     *
     * @param courseId the id of the course
     * @return the number of responses
     */
    public long countMoreFeedbackRequestResponsesByCourseId(long courseId) {
        return complaintResponseRepository.countByComplaint_Result_Participation_Exercise_Course_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(courseId,
                ComplaintType.MORE_FEEDBACK);
    }

    public long countComplaintsByExerciseId(long exerciseId) {
        return complaintRepository.countComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
    }

    public long countMoreFeedbackRequestsByExerciseId(long exerciseId) {
        return complaintRepository.countComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
    }

    /**
     * Calculates the number of unevaluated complaints and feedback requests for assessment dashboard participation graph
     *
     * @param examMode should be set to ignore the test run submissions
     * @param exercises the exercises for which the numbers of unevaluated complaints should be calculated
     */
    public void calculateNrOfOpenComplaints(Set<Exercise> exercises, boolean examMode) {
        final List<ExerciseMapEntry> numberOfComplaintsOfExercise;
        final List<ExerciseMapEntry> numberOfComplaintResponsesOfExercise;
        final List<ExerciseMapEntry> numberOfMoreFeedbackRequestsOfExercise;
        final List<ExerciseMapEntry> numberOfMoreFeedbackResponsesOfExercise;

        Set<Long> exerciseIds = exercises.stream().map(exercise -> exercise.getId()).collect(Collectors.toSet());

        if (examMode) {
            numberOfComplaintsOfExercise = complaintRepository.countComplaintsByExerciseIdsAndComplaintTypeIgnoreTestRuns(exerciseIds, ComplaintType.COMPLAINT);
            numberOfComplaintResponsesOfExercise = complaintResponseRepository.countComplaintsByExerciseIdsAndComplaintComplaintTypeIgnoreTestRuns(exerciseIds,
                    ComplaintType.COMPLAINT);
            numberOfMoreFeedbackRequestsOfExercise = new ArrayList<>();
            numberOfMoreFeedbackResponsesOfExercise = new ArrayList<>();
        }
        else {
            numberOfComplaintsOfExercise = complaintRepository.countComplaintsByExerciseIdsAndComplaintType(exerciseIds, ComplaintType.COMPLAINT);
            numberOfComplaintResponsesOfExercise = complaintResponseRepository.countComplaintsByExerciseIdsAndComplaintComplaintType(exerciseIds, ComplaintType.COMPLAINT);

            numberOfMoreFeedbackRequestsOfExercise = complaintRepository.countComplaintsByExerciseIdsAndComplaintType(exerciseIds, ComplaintType.MORE_FEEDBACK);
            numberOfMoreFeedbackResponsesOfExercise = complaintResponseRepository.countComplaintsByExerciseIdsAndComplaintComplaintType(exerciseIds, ComplaintType.MORE_FEEDBACK);
        }
        var numberOfComplaintsMap = numberOfComplaintsOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::getKey, entry -> entry.getValue()));
        var numberOfComplaintResponsesMap = numberOfComplaintResponsesOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::getKey, entry -> entry.getValue()));
        var numberOfMoreFeedbackRequestsMap = numberOfMoreFeedbackRequestsOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::getKey, entry -> entry.getValue()));
        var numberOfMoreFeedbackResponsesMap = numberOfMoreFeedbackResponsesOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::getKey, entry -> entry.getValue()));
        exercises.forEach(exercise -> {
            exercise.setNumberOfOpenComplaints(numberOfComplaintsMap.getOrDefault(exercise.getId(), 0L) - numberOfComplaintResponsesMap.getOrDefault(exercise.getId(), 0L));
            exercise.setNumberOfComplaints(numberOfComplaintsMap.getOrDefault(exercise.getId(), 0L));

            exercise.setNumberOfOpenMoreFeedbackRequests(
                    numberOfMoreFeedbackRequestsMap.getOrDefault(exercise.getId(), 0L) - numberOfMoreFeedbackResponsesMap.getOrDefault(exercise.getId(), 0L));
            exercise.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequestsMap.getOrDefault(exercise.getId(), 0L));
        });
    }

    /**
     * Given an exercise id, retrieve more feedback requests related to whoever is calling the method. Useful for creating a list of more feedback requests a tutor can review.
     *
     * @param exerciseId - the id of the exercise we are interested in
     * @return a list of complaints
     */
    public List<Complaint> getMyMoreFeedbackRequests(long exerciseId) {
        return complaintRepository.getAllComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
    }

    public List<Complaint> getAllComplaintsByTutorId(Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_Id(tutorId);
    }

    public List<Complaint> getAllComplaintsByCourseId(Long courseId) {
        return complaintRepository.getAllByResult_Participation_Exercise_Course_Id(courseId);
    }

    public List<Complaint> getAllComplaintsByExamId(Long examId) {
        return complaintRepository.getAllByResult_Participation_Exercise_ExerciseGroup_Exam_Id(examId);
    }

    public List<Complaint> getAllComplaintsByCourseIdAndTutorId(Long courseId, Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Course_Id(tutorId, courseId);
    }

    public List<Complaint> getAllComplaintsByExerciseId(Long exerciseId) {
        return complaintRepository.getAllByResult_Participation_Exercise_Id(exerciseId);
    }

    /**
     * Returns the maximum allowed number of complaints per participant in a course (differentiates between individual and team complaints)
     * @param course Course for which to evaluate
     * @param participant Participant for which to evaluate
     * @return max complaints
     */
    public Integer getMaxComplaintsPerParticipant(Course course, Participant participant) {
        return participant instanceof Team ? course.getMaxTeamComplaints() : course.getMaxComplaints();
    }

    public List<Complaint> getAllComplaintsByExerciseIdAndTutorId(Long exerciseId, Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Id(tutorId, exerciseId);
    }

    /**
     * This function checks whether the student is allowed to submit a complaint / more feedback request or not.
     * This is allowed within the corresponding number of days set in the course after the student received the result.
     * If the result was submitted after the assessment due date or the assessment due date is not set, the completion date of the result is checked.
     * If the result was submitted before the assessment due date, the assessment due date is checked, as the student can only see the result after the assessment due date.
     */
    private static void validateTimeOfComplaintOrRequestMoreFeedback(Result result, Exercise exercise, Course course, ComplaintType type) {
        int maxDays = switch (type) {
            case COMPLAINT -> course.getMaxComplaintTimeDays();
            case MORE_FEEDBACK -> course.getMaxRequestMoreFeedbackTimeDays();
        };

        boolean isTimeValid;
        if (exercise.getAssessmentDueDate() == null || result.getCompletionDate().isAfter(exercise.getAssessmentDueDate())) {
            isTimeValid = result.getCompletionDate().isAfter(ZonedDateTime.now().minusDays(maxDays));
        }
        else {
            isTimeValid = exercise.getAssessmentDueDate().isAfter(ZonedDateTime.now().minusDays(maxDays));
        }

        if (!isTimeValid) {
            String message = "You cannot " + (type == ComplaintType.COMPLAINT ? "submit a complaint" : "request more feedback") + " for a result that is older than "
                    + switch (maxDays) {
                    case 1 -> "one day";
                    case 7 -> "one week";
                    default -> maxDays % 7 == 0 ? (maxDays / 7) + " weeks" : maxDays + " days";
                    } + ".";
            throw new BadRequestAlertException(message, ENTITY_NAME, "complaintOrRequestMoreFeedbackTimeInvalid");
        }
    }

    /**
     * This function checks whether the student is allowed to submit a complaint or not for Exams. Submitting a complaint is allowed within the student exam review period.
     * This period is defined by {@link Exam#getExamStudentReviewStart()} and {@link Exam#getExamStudentReviewEnd()}
     */
    private static boolean isTimeOfComplaintValid(Exam exam) {
        if (exam.getExamStudentReviewStart() != null && exam.getExamStudentReviewEnd() != null) {
            return exam.getExamStudentReviewStart().isBefore(ZonedDateTime.now()) && exam.getExamStudentReviewEnd().isAfter(ZonedDateTime.now());
        }
        return false;
    }
}

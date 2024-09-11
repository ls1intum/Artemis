package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.dashboard.ExerciseMapEntry;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintResponseRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.dto.ComplaintRequestDTO;

/**
 * Service for managing complaints.
 */
@Profile(PROFILE_CORE)
@Service
public class ComplaintService {

    private static final String ENTITY_NAME = "complaint";

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultRepository resultRepository;

    private final UserRepository userRepository;

    private final ExamRepository examRepository;

    private final TeamRepository teamRepository;

    public ComplaintService(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, ResultRepository resultRepository,
            ExamRepository examRepository, UserRepository userRepository, TeamRepository teamRepository) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.resultRepository = resultRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Create a new complaint by checking if the user is still allowed to submit complaints and in the case of normal course exercises
     * whether the user still enough complaints left.
     *
     * @param complaintRequest the complaint to create
     * @param principal        the current Principal
     * @param examId           the optional examId. This is only set if the exercise is an exam exercise
     * @return the saved complaint
     */
    public Complaint createComplaint(ComplaintRequestDTO complaintRequest, Optional<Long> examId, Principal principal) {
        Result originalResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(complaintRequest.resultId())
                .orElseThrow(() -> new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "resultnotfound"));

        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        Participant participant = studentParticipation.getParticipant(); // Team or Student

        // Retrieve course to get Max Complaints, Max Team Complaints and Max Complaint Time
        final Course course = studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember();

        Long courseId = course.getId();

        // Check whether the complaint text limit is exceeded
        int maxLength = course.getMaxComplaintTextLimitForExercise(studentParticipation.getExercise());
        if (maxLength < complaintRequest.complaintText().length()) {
            throw new BadRequestAlertException("You cannot submit a complaint that exceeds the maximum number of " + maxLength + " characters", ENTITY_NAME,
                    "exceededComplaintTextLimit");
        }

        // checking if it is allowed to create a complaint
        if (examId.isPresent()) {
            final Exam exam = examRepository.findByIdElseThrow(examId.get());
            final Set<User> instructors = userRepository.getInstructors(exam.getCourse());
            boolean examTestRun = instructors.stream().anyMatch(instructor -> instructor.getLogin().equals(principal.getName()));
            if (!examTestRun && !isTimeOfComplaintValid(exam)) {
                throw new BadRequestAlertException("You cannot submit a complaint after the student review period", ENTITY_NAME, "afterStudentReviewPeriod");
            }
        }
        else {
            switch (complaintRequest.complaintType()) {
                case COMPLAINT -> {
                    long numberOfUnacceptedComplaints = countUnacceptedComplaintsByParticipantAndCourseId(participant, courseId);
                    long numberOfAllowedComplaintsInCourse = getMaxComplaintsPerParticipant(course, participant);
                    if (numberOfUnacceptedComplaints >= numberOfAllowedComplaintsInCourse) {
                        throw new BadRequestAlertException("You cannot have more than " + numberOfAllowedComplaintsInCourse + " open or rejected complaints at the same time.",
                                ENTITY_NAME, "tooManyComplaints");
                    }
                }
                case MORE_FEEDBACK -> {
                    if (!course.getRequestMoreFeedbackEnabled()) {
                        throw new BadRequestAlertException("You cannot request more feedback in this course because this feature has been disabled by the instructors.",
                                ENTITY_NAME, "moreFeedbackRequestsDisabled");
                    }
                }
            }
            validateTimeOfComplaintOrRequestMoreFeedback(originalResult, studentParticipation.getExercise(), studentParticipation, course, complaintRequest.complaintType());
        }

        if (studentParticipation.getParticipant() instanceof Team team) {
            studentParticipation.setParticipant(teamRepository.findWithStudentsByIdElseThrow(team.getId()));
        }

        if (!studentParticipation.isOwnedBy(principal.getName())) {
            throw new BadRequestAlertException("You can create a complaint only for a result you submitted", ENTITY_NAME, "differentuser");
        }

        originalResult.setHasComplaint(true);
        // When a student complains, a tutor has to manually correct the submission, so it is no longer automatic
        if (originalResult.getAssessmentType() == AssessmentType.AUTOMATIC) {
            originalResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        }
        Complaint complaint = new Complaint();
        complaint.setComplaintText(complaintRequest.complaintText());
        complaint.setComplaintType(complaintRequest.complaintType());
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
     * @param courseId    the id of the course
     * @return the number of unaccepted complaints
     */
    public long countUnacceptedComplaintsByParticipantAndCourseId(Participant participant, long courseId) {
        if (participant instanceof User) {
            return complaintRepository.countUnacceptedComplaintsByStudentIdAndCourseId(participant.getId(), courseId);
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

    /**
     * Calculates the number of unevaluated complaints and feedback requests for assessment dashboard participation graph
     *
     * @param examMode  should be set to ignore the test run submissions
     * @param exercises the exercises for which the numbers of unevaluated complaints should be calculated
     */
    public void calculateNrOfOpenComplaints(Set<Exercise> exercises, boolean examMode) {
        if (exercises.isEmpty()) {
            return;
        }
        List<ExerciseMapEntry> numberOfComplaintsOfExercise = List.of();
        List<ExerciseMapEntry> numberOfComplaintResponsesOfExercise = List.of();
        List<ExerciseMapEntry> numberOfMoreFeedbackRequestsOfExercise = List.of();
        List<ExerciseMapEntry> numberOfMoreFeedbackResponsesOfExercise = List.of();

        Set<Long> exerciseIds = exercises.stream().map(DomainObject::getId).collect(Collectors.toSet());
        // only invoke the query for non empty exercise sets to avoid performance issues
        if (!exerciseIds.isEmpty()) {

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
                numberOfMoreFeedbackResponsesOfExercise = complaintResponseRepository.countComplaintsByExerciseIdsAndComplaintComplaintType(exerciseIds,
                        ComplaintType.MORE_FEEDBACK);
            }
        }
        var numberOfComplaintsMap = numberOfComplaintsOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        var numberOfComplaintResponsesMap = numberOfComplaintResponsesOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        var numberOfMoreFeedbackRequestsMap = numberOfMoreFeedbackRequestsOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        var numberOfMoreFeedbackResponsesMap = numberOfMoreFeedbackResponsesOfExercise.stream().collect(Collectors.toMap(ExerciseMapEntry::exerciseId, ExerciseMapEntry::value));
        exercises.forEach(exercise -> {
            exercise.setNumberOfOpenComplaints(numberOfComplaintsMap.getOrDefault(exercise.getId(), 0L) - numberOfComplaintResponsesMap.getOrDefault(exercise.getId(), 0L));
            exercise.setNumberOfComplaints(numberOfComplaintsMap.getOrDefault(exercise.getId(), 0L));

            exercise.setNumberOfOpenMoreFeedbackRequests(
                    numberOfMoreFeedbackRequestsMap.getOrDefault(exercise.getId(), 0L) - numberOfMoreFeedbackResponsesMap.getOrDefault(exercise.getId(), 0L));
            exercise.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequestsMap.getOrDefault(exercise.getId(), 0L));
        });
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
     *
     * @param course      Course for which to evaluate
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
     * This starts from the latest of these three points in time: Completion date of result, due date, assessment due date of exercise
     *
     * @param result               the result for which a complaint should be filed
     * @param exercise             the exercise where the student wants to complain
     * @param studentParticipation StudentParticipation the participation which might contain an individual due date
     * @param course               the course specifying the number of available days for the complaint
     * @param type                 specifies if this is an actual complaint or a more feedback request
     */
    private static void validateTimeOfComplaintOrRequestMoreFeedback(Result result, Exercise exercise, StudentParticipation studentParticipation, Course course,
            ComplaintType type) {
        int maxDays = switch (type) {
            case COMPLAINT -> course.getMaxComplaintTimeDays();
            case MORE_FEEDBACK -> course.getMaxRequestMoreFeedbackTimeDays();
        };

        if (result.getCompletionDate() == null) {
            throw new BadRequestAlertException("Cannot submit " + (type == ComplaintType.COMPLAINT ? "complaint" : "more feedback request ") + " for an uncompleted result.",
                    ENTITY_NAME, "complaintOrRequestMoreFeedbackNotCompleted");
        }
        if (!Boolean.TRUE.equals(result.isRated()) && !exercise.getAllowComplaintsForAutomaticAssessments()) {
            throw new BadRequestAlertException("Cannot submit " + (type == ComplaintType.COMPLAINT ? "complaint" : "more feedback request ")
                    + " for an unrated result with no complaints on automatic assessment.", ENTITY_NAME, "complaintOrRequestMoreFeedbackNotGraded");
        }

        final ZonedDateTime complaintStartDate = getComplaintStartDate(exercise, studentParticipation, result);
        boolean isTimeValid = ZonedDateTime.now().isBefore(complaintStartDate.plusDays(maxDays));

        if (!isTimeValid) {
            String timeForComplaint = switch (maxDays) {
                case 1 -> "one day";
                case 7 -> "one week";
                default -> maxDays % 7 == 0 ? (maxDays / 7) + " weeks" : maxDays + " days";
            };
            String message = "You cannot " + (type == ComplaintType.COMPLAINT ? "submit a complaint" : "request more feedback") + " for a result that is older than "
                    + timeForComplaint + ".";
            throw new BadRequestAlertException(message, ENTITY_NAME, "complaintOrRequestMoreFeedbackTimeInvalid");
        }
    }

    /**
     * Obtains the time when the complaint period starts.
     * <p>
     * Assumes the {@link Result#getCompletionDate()} to be present.
     *
     * @param exercise             The exercise for which complaints can be submitted.
     * @param studentParticipation The participation for which a complaint should be submitted.
     * @param result               The result which the complaint is about.
     * @return The time from which submitting a complaint is possible.
     */
    private static ZonedDateTime getComplaintStartDate(final Exercise exercise, final StudentParticipation studentParticipation, final Result result) {
        final List<ZonedDateTime> possibleComplaintStartDates = new ArrayList<>();
        possibleComplaintStartDates.add(result.getCompletionDate());

        final Optional<ZonedDateTime> relevantDueDate = Optional.ofNullable(studentParticipation).flatMap(ExerciseDateService::getDueDate);
        relevantDueDate.ifPresent(possibleComplaintStartDates::add);

        if (exercise.getAssessmentDueDate() != null) {
            possibleComplaintStartDates.add(exercise.getAssessmentDueDate());
        }

        return possibleComplaintStartDates.stream().max(Comparator.naturalOrder())
                .orElseThrow(() -> new NoSuchElementException("Expected at least the result completion date to be present."));
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

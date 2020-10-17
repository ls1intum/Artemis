package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service for managing complaints.
 */
@Service
public class ComplaintService {

    private static final String ENTITY_NAME = "complaint";

    private ComplaintRepository complaintRepository;

    private ResultRepository resultRepository;

    private ResultService resultService;

    private CourseService courseService;

    private UserService userService;

    private ExamService examService;

    public ComplaintService(ComplaintRepository complaintRepository, ResultRepository resultRepository, ResultService resultService, CourseService courseService,
            ExamService examService, UserService userService) {
        this.complaintRepository = complaintRepository;
        this.resultRepository = resultRepository;
        this.resultService = resultService;
        this.courseService = courseService;
        this.examService = examService;
        this.userService = userService;
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
    @Transactional
    public Complaint createComplaint(Complaint complaint, OptionalLong examId, Principal principal) {
        Result originalResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(complaint.getResult().getId())
                .orElseThrow(() -> new BadRequestAlertException("The result you are referring to does not exist", ENTITY_NAME, "resultnotfound"));
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        Participant participant = studentParticipation.getParticipant(); // Team or Student
        Long courseId = studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId();

        if (examId.isPresent()) {
            final Exam exam = examService.findOne(examId.getAsLong());
            final List<User> instructors = userService.getInstructors(exam.getCourse());
            boolean examTestRun = instructors.stream().anyMatch(instructor -> instructor.getLogin().equals(principal.getName()));
            if (!examTestRun && !isTimeOfComplaintValid(exam)) {
                throw new BadRequestAlertException("You cannot submit a complaint after the student review period", ENTITY_NAME, "afterStudentReviewPeriod");
            }
        }
        else {
            // Retrieve course to get Max Complaints, Max Team Complaints and Max Complaint Time
            final Course course = courseService.findOne(courseId);

            if (complaint.getComplaintType() == ComplaintType.COMPLAINT) {
                long numberOfUnacceptedComplaints = countUnacceptedComplaintsByParticipantAndCourseId(participant, courseId);
                long numberOfAllowedComplaintsInCourse = getMaxComplaintsPerParticipant(course, participant);
                if (numberOfUnacceptedComplaints >= numberOfAllowedComplaintsInCourse) {
                    throw new BadRequestAlertException("You cannot have more than " + numberOfAllowedComplaintsInCourse + " open or rejected complaints at the same time.",
                            ENTITY_NAME, "toomanycomplaints");
                }
            }
            else if (complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
                if (!course.getRequestMoreFeedbackEnabled()) {
                    throw new BadRequestAlertException("You cannot request more feedback in this course because this feature has been disabled by the instructors.", ENTITY_NAME,
                            "moreFeedbackRequestsDisabled");
                }
            }
            if (!isTimeOfComplaintValid(originalResult, studentParticipation.getExercise(), course)) {
                throw new BadRequestAlertException("You cannot submit a complaint for a result that is older than one week.", ENTITY_NAME, "resultolderthanaweek");
            }
        }

        if (!studentParticipation.isOwnedBy(principal.getName())) {
            throw new BadRequestAlertException("You can create a complaint only for a result you submitted", ENTITY_NAME, "differentuser");
        }

        originalResult.setHasComplaint(true);

        complaint.setSubmittedTime(ZonedDateTime.now());
        complaint.setParticipant(participant);
        complaint.setResult(originalResult);
        try {
            // Store the original result with the complaint
            complaint.setResultBeforeComplaint(resultService.getOriginalResultAsString(originalResult));
        }
        catch (JsonProcessingException exception) {
            throw new InternalServerErrorException("Failed to store original result");
        }

        resultRepository.save(originalResult);

        return complaintRepository.save(complaint);
    }

    @Transactional(readOnly = true)
    public Optional<Complaint> getById(long complaintId) {
        return complaintRepository.findById(complaintId);
    }

    @Transactional(readOnly = true)
    public Optional<Complaint> getByResultId(long resultId) {
        return complaintRepository.findByResult_Id(resultId);
    }

    /**
     * Count the number of unaccepted complaints of a student or team in a given course. Unaccepted means that they are either open/unhandled or rejected. We use this to limit the
     * number of complaints for a student or team in a course. Requests for more feedback are not counted here.
     *
     * @param participant the participant (student or team)
     * @param courseId  the id of the course
     * @return the number of unaccepted complaints
     */
    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public long countComplaintsByCourseId(long courseId) {
        return complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.COMPLAINT);
    }

    @Transactional(readOnly = true)
    public long countMoreFeedbackRequestsByCourseId(long courseId) {
        return complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(courseId, ComplaintType.MORE_FEEDBACK);
    }

    @Transactional(readOnly = true)
    public long countComplaintsByExerciseId(long exerciseId) {
        return complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
    }

    @Transactional(readOnly = true)
    public long countMoreFeedbackRequestsByExerciseId(long exerciseId) {
        return complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
    }

    /**
     * Given an exercise id, retrieve all the complaints apart the ones related to whoever is calling the method. Useful for creating a list of complaints a tutor can review.
     *
     * @param exerciseId - the id of the exercise we are interested in
     * @return a list of complaints
     */
    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByExerciseIdButMine(long exerciseId) {
        return complaintRepository.findByResult_Participation_Exercise_Id_ComplaintTypeWithEagerSubmissionAndEagerAssessor(exerciseId, ComplaintType.COMPLAINT);
    }

    /**
     * Given an exercise id, retrieve more feedback requests related to whoever is calling the method. Useful for creating a list of more feedback requests a tutor can review.
     *
     * @param exerciseId - the id of the exercise we are interested in
     * @return a list of complaints
     */
    @Transactional(readOnly = true)
    public List<Complaint> getMyMoreFeedbackRequests(long exerciseId) {
        return complaintRepository.findByResult_Participation_Exercise_Id_ComplaintTypeWithEagerSubmissionAndEagerAssessor(exerciseId, ComplaintType.MORE_FEEDBACK);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByTutorId(Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_Id(tutorId);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByCourseId(Long courseId) {
        return complaintRepository.getAllByResult_Participation_Exercise_Course_Id(courseId);
    }

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByCourseIdAndTutorId(Long courseId, Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Course_Id(tutorId, courseId);
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public List<Complaint> getAllComplaintsByExerciseIdAndTutorId(Long exerciseId, Long tutorId) {
        return complaintRepository.getAllByResult_Assessor_IdAndResult_Participation_Exercise_Id(tutorId, exerciseId);
    }

    /**
     * This function checks whether the student is allowed to submit a complaint or not. Submitting a complaint is allowed within one week after the student received the result. If
     * the result was submitted after the assessment due date or the assessment due date is not set, the completion date of the result is checked. If the result was submitted
     * before the assessment due date, the assessment due date is checked, as the student can only see the result after the assessment due date.
     */
    private boolean isTimeOfComplaintValid(Result result, Exercise exercise, Course course) {
        if (exercise.getAssessmentDueDate() == null || result.getCompletionDate().isAfter(exercise.getAssessmentDueDate())) {
            return result.getCompletionDate().isAfter(ZonedDateTime.now().minusDays(course.getMaxComplaintTimeDays()));
        }
        return exercise.getAssessmentDueDate().isAfter(ZonedDateTime.now().minusDays(course.getMaxComplaintTimeDays()));
    }

    /**
     * This function checks whether the student is allowed to submit a complaint or not for Exams. Submitting a complaint is allowed within the student exam review period.
     * This period is defined by {@link Exam#getExamStudentReviewStart()} and {@link Exam#getExamStudentReviewEnd()}
     */
    private boolean isTimeOfComplaintValid(Exam exam) {
        if (exam.getExamStudentReviewStart() != null && exam.getExamStudentReviewEnd() != null) {
            return exam.getExamStudentReviewStart().isBefore(ZonedDateTime.now()) && exam.getExamStudentReviewEnd().isAfter(ZonedDateTime.now());
        }
        return false;
    }
}

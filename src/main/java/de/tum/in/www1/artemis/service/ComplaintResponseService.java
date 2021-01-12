package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.web.rest.ComplaintResponseResource;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.ComplaintResponseLockedException;

/**
 * Service for managing complaint responses.
 */
@Service
public class ComplaintResponseService {

    private final Logger log = LoggerFactory.getLogger(ComplaintResponseResource.class);

    private ComplaintRepository complaintRepository;

    private ComplaintResponseRepository complaintResponseRepository;

    private UserService userService;

    private AuthorizationCheckService authorizationCheckService;

    public ComplaintResponseService(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, UserService userService,
            AuthorizationCheckService authorizationCheckService) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.userService = userService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Removes the empty complaint response and thus the lock for a given complaint
     *
     * The empty complaint response acts as a lock. Only the reviewer of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. This methods removes the current empty complaint response thus removing the lock
     *
     * @param complaintId if of the complaint for which to remove the empty response for
     */
    @Transactional // ok because of modifying query
    public void removeEmptyComplaintResponse(Long complaintId) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Complaint id should not be null");
        }
        Optional<Complaint> complaintFromDatabaseOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (complaintFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("Complaint was not found in database");
        }
        Complaint complaintFromDatabase = complaintFromDatabaseOptional.get();
        ComplaintResponse unfinishedComplaintResponse = getUnfinishedComplaintResponse(complaintFromDatabase);

        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for removing the lock on the complaint");
        }
        // only instructors and the original reviewer can remove the lock while it is still running
        if (blockedByLock(unfinishedComplaintResponse, user, studentParticipation)) {
            throw new ComplaintResponseLockedException(unfinishedComplaintResponse);
        }
        complaintResponseRepository.deleteById(unfinishedComplaintResponse.getId());
        log.debug("Removed empty complaint and thus lock for complaint with id : {}", complaintId);
    }

    private ComplaintResponse getUnfinishedComplaintResponse(Complaint complaintFromDatabase) {
        if (complaintFromDatabase.isAccepted() != null) {
            throw new IllegalArgumentException("Complaint is already handled");
        }
        if (complaintFromDatabase.getComplaintResponse() == null) {
            throw new IllegalArgumentException("Complaint response does not exists for given complaint");
        }
        if (complaintFromDatabase.getComplaintResponse().getSubmittedTime() != null) {
            throw new IllegalArgumentException("Complaint response is already submitted");
        }
        return complaintFromDatabase.getComplaintResponse();
    }

    /**
     * Refreshes the empty complaint response for a given complaint that acts a lock
     *
     * The empty complaint response acts as a lock. Only the reviewer of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. This methods exchanges the current empty complaint response to a new one
     * thus updating the lock.
     *
     * This is possible in two cases:
     *
     * Case A: Lock is currently active -> Only the initial reviewer or an instructor can refresh the empty complaint response
     * Case B: Lock has run out --> Others teaching assistants can refresh the empty complaint response thus acquiring the lock
     *
     * @param complaintId if of the complaint for which to refresh the empty response for
     * @return refreshed empty complaint response
     */
    @Transactional // ok because of modifying query
    public ComplaintResponse refreshEmptyComplaintResponse(Long complaintId) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Complaint id should not be null");
        }
        Optional<Complaint> complaintFromDatabaseOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (complaintFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("Complaint was not found in database");
        }
        Complaint complaintFromDatabase = complaintFromDatabaseOptional.get();
        ComplaintResponse unfinishedComplaintResponse = getUnfinishedComplaintResponse(complaintFromDatabase);

        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for refreshing the empty complaint response");
        }
        // only instructors and the original reviewer can refresh the lock while it is still running

        if (blockedByLock(unfinishedComplaintResponse, user, studentParticipation)) {
            throw new ComplaintResponseLockedException(unfinishedComplaintResponse);
        }

        complaintResponseRepository.deleteById(unfinishedComplaintResponse.getId());
        complaintFromDatabase.setComplaintResponse(null);
        complaintResponseRepository.flush();

        ComplaintResponse refreshedEmptyComplaintResponse = new ComplaintResponse();
        refreshedEmptyComplaintResponse.setReviewer(user); // owner of the lock
        refreshedEmptyComplaintResponse.setComplaint(complaintFromDatabase);
        ComplaintResponse persistedComplaintResponse = complaintResponseRepository.save(refreshedEmptyComplaintResponse);
        log.debug("Refreshed empty complaint and thus lock for complaint with id : {}", complaintId);
        return persistedComplaintResponse;
    }

    /**
     * Creates an empty complaint response for a given complaint that acts a lock
     *
     * The empty complaint response acts as a lock. Only the creator of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}
     *
     * @param complaintId id of the complaint for which to create an empty complaint response for
     * @return persisted empty complaint response
     */
    public ComplaintResponse createEmptyComplaintResponse(Long complaintId) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Complaint id should not be null");
        }
        Optional<Complaint> complaintFromDatabaseOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (complaintFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("Complaint was not found in database");
        }
        Complaint complaintFromDatabase = complaintFromDatabaseOptional.get();
        if (complaintFromDatabase.isAccepted() != null) {
            throw new IllegalArgumentException("Complaint is already handled");
        }
        if (complaintFromDatabase.getComplaintResponse() != null) {
            throw new IllegalArgumentException("Complaint response already exists for given complaint");
        }
        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for creating the empty complaint response");
        }
        ComplaintResponse emptyComplaintResponse = new ComplaintResponse();
        emptyComplaintResponse.setReviewer(user); // owner of the lock
        emptyComplaintResponse.setComplaint(complaintFromDatabase);
        ComplaintResponse persistedComplaintResponse = complaintResponseRepository.save(emptyComplaintResponse);
        log.debug("Created empty complaint and thus lock for complaint with id : {}", complaintId);
        return persistedComplaintResponse;
    }

    /**
     * Resolves a complaint by filling in the empty complaint response attached to it
     *
     * The empty complaint response acts as a lock. Only the creator of the empty complaint response and instructors can resolve empty complaint response as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. This methods fill in the initial complaint response and either accepts
     * or denies the associated complaint, thus resolving the complaint
     *
     * @param updatedComplaintResponse complaint response containing the information necessary for resolving the complaint
     * @return complaintResponse of resolved complaint
     */
    @Transactional // ok because of modifying query
    public ComplaintResponse resolveComplaint(ComplaintResponse updatedComplaintResponse) {
        if (updatedComplaintResponse.getId() == null) {
            throw new IllegalArgumentException("The complaint response needs to have an id");
        }
        Optional<ComplaintResponse> complaintResponseFromDatabaseOptional = complaintResponseRepository.findById(updatedComplaintResponse.getId());
        if (complaintResponseFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("The complaint response was not found in the database");
        }
        ComplaintResponse emptyComplaintResponseFromDatabase = complaintResponseFromDatabaseOptional.get();
        if (emptyComplaintResponseFromDatabase.getSubmittedTime() != null || emptyComplaintResponseFromDatabase.getResponseText() != null) {
            throw new IllegalArgumentException("The complaint response is not empty");
        }
        Optional<Complaint> originalComplaintOptional = complaintRepository.findByIdWithEagerAssessor(emptyComplaintResponseFromDatabase.getComplaint().getId());
        if (originalComplaintOptional.isEmpty()) {
            throw new IllegalArgumentException("The complaint was not found in the database");
        }
        Complaint originalComplaint = originalComplaintOptional.get();
        if (originalComplaint.isAccepted() != null) {
            throw new IllegalArgumentException("You can not update the response to an already answered complaint");
        }
        if (updatedComplaintResponse.getComplaint().isAccepted() == null) {
            throw new IllegalArgumentException("You need to either accept or reject a complaint");
        }

        Result originalResult = originalComplaint.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, originalComplaint, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for resolving the complaint");
        }
        // only instructors and the original reviewer can ignore the lock on a complaint response
        if (blockedByLock(emptyComplaintResponseFromDatabase, user, studentParticipation)) {
            throw new ComplaintResponseLockedException(emptyComplaintResponseFromDatabase);
        }

        originalComplaint.setAccepted(updatedComplaintResponse.getComplaint().isAccepted()); // accepted or denied
        originalComplaint = complaintRepository.save(originalComplaint);

        emptyComplaintResponseFromDatabase.setSubmittedTime(ZonedDateTime.now());
        emptyComplaintResponseFromDatabase.setResponseText(updatedComplaintResponse.getResponseText());
        emptyComplaintResponseFromDatabase.setComplaint(originalComplaint);
        emptyComplaintResponseFromDatabase.setReviewer(user);
        return complaintResponseRepository.save(emptyComplaintResponseFromDatabase);
    }

    /**
     * Checks if an user is blocked by a lock
     * @param emptyComplaintResponseFromDatabase the lock
     * @param user user to check
     * @param studentParticipation used to find out if user is instructor of exercise
     * @return true if blocked by lock, false otherwise
     */
    public boolean blockedByLock(ComplaintResponse emptyComplaintResponseFromDatabase, User user, StudentParticipation studentParticipation) {
        return emptyComplaintResponseFromDatabase.isCurrentlyLocked()
                && !(authorizationCheckService.isAtLeastInstructorForExercise(studentParticipation.getExercise()) || emptyComplaintResponseFromDatabase.getReviewer().equals(user));
    }

    /**
     * Checks whether the reviewer is authorized to respond to this complaint
     *
     * 1. Team Exercises
     *    => The team tutor assesses the submissions and responds to complaints and more feedback requests
     *
     * 2. Individual Exercises
     *    => Complaints can only be handled by a tutor who is not the original assessor
     *    => Complaints of exam test runs can be assessed by instructors. They are identified by the same user being the assessor and student
     *    => More feedback requests are handled by the assessor himself
     *
     * @param participation Participation to which the complaint belongs to
     * @param complaint Complaint for which to check
     * @param assessor Assessor of the submission whose assessment was complained about
     * @param reviewer Reviewer who is trying to create a response to the complaint
     * @return true if the tutor is allowed to respond to the complaint, false otherwise
     */
    private boolean isUserAuthorizedToRespondToComplaint(StudentParticipation participation, Complaint complaint, User assessor, User reviewer) {
        if (!authorizationCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise())) {
            return false;
        }
        if (participation.getParticipant() instanceof Team) {
            return assessor.getLogin().equals(reviewer.getLogin());
        }
        else if (complaint.getComplaintType() == null || complaint.getComplaintType().equals(ComplaintType.COMPLAINT)) {
            // if test run complaint
            if (complaint.getStudent() != null && complaint.getStudent().getLogin().equals(assessor.getLogin())
                    && authorizationCheckService.isAtLeastInstructorForExercise(participation.getExercise())) {
                return true;
            }
            return !assessor.getLogin().equals(reviewer.getLogin());
        }
        else if (complaint.getComplaintType() != null && complaint.getComplaintType().equals(ComplaintType.MORE_FEEDBACK)) {
            return assessor.getLogin().equals(reviewer.getLogin());
        }
        return false;
    }
}

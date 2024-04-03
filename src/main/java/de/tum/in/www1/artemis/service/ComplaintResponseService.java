package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.dto.ComplaintResponseUpdateDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ComplaintResponseLockedException;

/**
 * Service for managing complaint responses.
 */
@Profile(PROFILE_CORE)
@Service
public class ComplaintResponseService {

    private static final String ENTITY_NAME = "complaintResponse";

    private static final Logger log = LoggerFactory.getLogger(ComplaintResponseService.class);

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ComplaintResponseService(ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Removes the empty complaint response and thus the lock for a given complaint
     *
     * The empty complaint response acts as a lock. Only the reviewer of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. These methods remove the current empty complaint response thus removing the
     * lock
     *
     * @param complaint the complaint for which to remove the empty response for
     */
    public void removeComplaintResponseRepresentingLock(Complaint complaint) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint should not be null");
        }
        ComplaintResponse complaintResponseRepresentingLock = getComplaintResponseRepresentingALock(complaint);

        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (!isUserAuthorizedToRespondToComplaint(complaint, user)) {
            throw new AccessForbiddenException("Insufficient permission for removing the lock on the complaint");
        }
        // only instructors and the original reviewer can remove the lock while it is still running
        if (blockedByLock(complaintResponseRepresentingLock, user)) {
            throw new ComplaintResponseLockedException(complaintResponseRepresentingLock);
        }
        complaintResponseRepository.deleteById(complaintResponseRepresentingLock.getId());
        log.debug("Removed empty complaint and thus lock for complaint with id : {}", complaint.getId());
    }

    private ComplaintResponse getComplaintResponseRepresentingALock(Complaint complaint) {
        if (complaint.isAccepted() != null) {
            throw new IllegalArgumentException("Complaint is already handled, thus no locks exists");
        }
        if (complaint.getComplaintResponse() == null) {
            throw new IllegalArgumentException("Complaint response does not exists for given complaint, thus no lock exists");
        }
        if (complaint.getComplaintResponse().getSubmittedTime() != null) {
            throw new IllegalArgumentException("Complaint response is already submitted, thus not representing a lock");
        }
        return complaint.getComplaintResponse();
    }

    /**
     * Refreshes the empty complaint response for a given complaint that acts a lock
     *
     * The empty complaint response acts as a lock. Only the reviewer of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. These methods exchange the current empty complaint response to a new one
     * thus updating the lock.
     *
     * This is possible in two cases:
     *
     * Case A: Lock is currently active -> Only the initial reviewer or an instructor can refresh the empty complaint response
     * Case B: Lock has run out --> Others teaching assistants can refresh the empty complaint response thus acquiring the lock
     *
     * @param complaint the complaint for which to refresh the empty response for
     * @return refreshed empty complaint response
     */
    public ComplaintResponse refreshComplaintResponseRepresentingLock(Complaint complaint) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint should not be null");
        }
        ComplaintResponse complaintResponseRepresentingLock = getComplaintResponseRepresentingALock(complaint);

        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (!isUserAuthorizedToRespondToComplaint(complaint, user)) {
            throw new AccessForbiddenException("Insufficient permission for refreshing the lock on the complaint");
        }
        // only instructors and the original reviewer can refresh the lock while it is still running
        if (blockedByLock(complaintResponseRepresentingLock, user)) {
            throw new ComplaintResponseLockedException(complaintResponseRepresentingLock);
        }

        complaintResponseRepository.deleteById(complaintResponseRepresentingLock.getId());
        complaint.setComplaintResponse(null);
        complaintResponseRepository.flush();

        ComplaintResponse refreshedEmptyComplaintResponse = new ComplaintResponse();
        refreshedEmptyComplaintResponse.setReviewer(user); // owner of the lock
        refreshedEmptyComplaintResponse.setComplaint(complaint);
        ComplaintResponse persistedComplaintResponse = complaintResponseRepository.save(refreshedEmptyComplaintResponse);
        log.debug("Refreshed empty complaint and thus lock for complaint with id : {}", complaint.getId());
        return persistedComplaintResponse;
    }

    /**
     * Creates an empty complaint response for a given complaint that acts a lock
     *
     * The empty complaint response acts as a lock. Only the creator of the empty complaint response and instructors can resolve the complaint as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}
     *
     * @param complaint complaint for which to create an empty complaint response for
     * @return persisted empty complaint response
     */
    public ComplaintResponse createComplaintResponseRepresentingLock(Complaint complaint) {
        if (complaint == null) {
            throw new IllegalArgumentException("Complaint should not be null");
        }
        if (complaint.isAccepted() != null) {
            throw new IllegalArgumentException("Complaint is already handled");
        }
        if (complaint.getComplaintResponse() != null) {
            throw new IllegalArgumentException("Complaint response already exists for given complaint");
        }
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (!isUserAuthorizedToRespondToComplaint(complaint, user)) {
            throw new AccessForbiddenException("Insufficient permission for creating the empty complaint response");
        }

        ComplaintResponse complaintResponseRepresentingLock = new ComplaintResponse();
        complaintResponseRepresentingLock.setReviewer(user); // owner of the lock
        complaintResponseRepresentingLock.setComplaint(complaint);
        ComplaintResponse persistedComplaintResponse = complaintResponseRepository.save(complaintResponseRepresentingLock);
        log.debug("Created empty complaint and thus lock for complaint with id : {}", complaint.getId());
        return persistedComplaintResponse;
    }

    /**
     * Resolves a complaint by filling in the empty complaint response attached to it
     *
     * The empty complaint response acts as a lock. Only the creator of the empty complaint response and instructors can resolve empty complaint response as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. These methods fill in the initial complaint response and either accepts
     * or denies the associated complaint, thus resolving the complaint
     *
     * @param updatedComplaintResponse complaint response containing the information necessary for resolving the complaint
     * @param complaintResponseId      complaint response id
     * @return complaintResponse of resolved complaint
     */
    public ComplaintResponse resolveComplaint(ComplaintResponseUpdateDTO updatedComplaintResponse, Long complaintResponseId) {
        validateComplaintResponseId(complaintResponseId);
        ComplaintResponse complaintResponseFromDatabase = complaintResponseRepository.fetchComplaintResponseOrThrow(complaintResponseId);
        validateComplaintResponseEmpty(complaintResponseFromDatabase);
        Complaint originalComplaint = complaintRepository.fetchOriginalComplaintOrThrow(complaintResponseFromDatabase);
        validateOriginalComplaintNotAnswered(originalComplaint);

        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        validateUserPermissionAndLockStatus(originalComplaint, complaintResponseFromDatabase, user);

        if (updatedComplaintResponse.complaintIsAccepted() == null) {
            throw new IllegalArgumentException("You need to either accept or reject a complaint");
        }

        validateResponseTextLimit(updatedComplaintResponse.responseText(), originalComplaint);

        originalComplaint.setAccepted(updatedComplaintResponse.complaintIsAccepted()); // accepted or denied
        originalComplaint = complaintRepository.save(originalComplaint);

        complaintResponseFromDatabase.setSubmittedTime(ZonedDateTime.now());
        complaintResponseFromDatabase.setResponseText(updatedComplaintResponse.responseText());
        complaintResponseFromDatabase.setComplaint(originalComplaint);
        complaintResponseFromDatabase.setReviewer(user);
        return complaintResponseRepository.save(complaintResponseFromDatabase);
    }

    /**
     * Resolves a complaint by filling in the empty complaint response attached to it
     *
     * The empty complaint response acts as a lock. Only the creator of the empty complaint response and instructors can resolve empty complaint response as long as the lock
     * is running. For lock duration calculation see: {@link ComplaintResponse#isCurrentlyLocked()}. These methods fill in the initial complaint response and either accepts
     * or denies the associated complaint, thus resolving the complaint
     *
     * @param updatedComplaintResponse complaint response containing the information necessary for resolving the complaint
     * @return complaintResponse of resolved complaint
     */
    public ComplaintResponse resolveComplaint(ComplaintResponse updatedComplaintResponse) {
        validateComplaintResponseId(updatedComplaintResponse.getId());
        ComplaintResponse complaintResponseFromDatabase = complaintResponseRepository.fetchComplaintResponseOrThrow(updatedComplaintResponse.getId()); // TODO: make this retrieval
                                                                                                                                                       // redundant by proper
                                                                                                                                                       // fetching
        validateComplaintResponseEmpty(complaintResponseFromDatabase);
        Complaint originalComplaint = complaintRepository.fetchOriginalComplaintOrThrow(complaintResponseFromDatabase); // TODO: make this retrieval redundant by proper fetching
        validateOriginalComplaintNotAnswered(originalComplaint);

        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        validateUserPermissionAndLockStatus(originalComplaint, complaintResponseFromDatabase, user);

        if (updatedComplaintResponse.getComplaint().isAccepted() == null) {
            throw new IllegalArgumentException("You need to either accept or reject a complaint");
        }

        validateResponseTextLimit(updatedComplaintResponse.getResponseText(), originalComplaint);

        originalComplaint.setAccepted(updatedComplaintResponse.getComplaint().isAccepted()); // accepted or denied
        originalComplaint = complaintRepository.save(originalComplaint);

        complaintResponseFromDatabase.setSubmittedTime(ZonedDateTime.now());
        complaintResponseFromDatabase.setResponseText(updatedComplaintResponse.getResponseText());
        complaintResponseFromDatabase.setComplaint(originalComplaint);
        complaintResponseFromDatabase.setReviewer(user);
        return complaintResponseRepository.save(complaintResponseFromDatabase);
    }

    private void validateComplaintResponseId(Long complaintResponseId) {
        if (complaintResponseId == null) {
            throw new IllegalArgumentException("The complaint response needs to have an id");
        }
    }

    private void validateComplaintResponseEmpty(ComplaintResponse complaintResponse) {
        if (complaintResponse.getSubmittedTime() != null || complaintResponse.getResponseText() != null) {
            throw new IllegalArgumentException("The complaint response is not empty");
        }
    }

    private void validateOriginalComplaintNotAnswered(Complaint originalComplaint) {
        if (originalComplaint.isAccepted() != null) {
            throw new IllegalArgumentException("You cannot update the response to an already answered complaint");
        }
    }

    private void validateResponseTextLimit(String responseText, Complaint originalComplaint) {
        if (responseText != null) {
            Course course = originalComplaint.getResult().getParticipation().getExercise().getCourseViaExerciseGroupOrCourseMember();
            int maxLength = course.getMaxComplaintResponseTextLimitForExercise(originalComplaint.getResult().getParticipation().getExercise());
            if (responseText.length() > maxLength) {
                throw new BadRequestAlertException("You cannot submit a complaint response that exceeds the maximum number of " + maxLength + " characters", ENTITY_NAME,
                        "exceededComplaintResponseTextLimit");
            }
        }
    }

    private void validateUserPermissionAndLockStatus(Complaint originalComplaint, ComplaintResponse complaintResponse, User user) {
        if (!isUserAuthorizedToRespondToComplaint(originalComplaint, user)) {
            throw new AccessForbiddenException("Insufficient permission for resolving the complaint");
        }

        if (blockedByLock(complaintResponse, user)) {
            throw new ComplaintResponseLockedException(complaintResponse);
        }
    }

    /**
     * Checks if a user is blocked by a complaintResponse representing a lock
     *
     * @param complaintResponseRepresentingLock the complaintResponse representing a lock
     * @param user                              user to check
     * @return true if blocked by lock, false otherwise
     */
    public boolean blockedByLock(ComplaintResponse complaintResponseRepresentingLock, User user) {
        if (user == null || complaintResponseRepresentingLock == null || complaintResponseRepresentingLock.getComplaint() == null
                || complaintResponseRepresentingLock.getComplaint().getResult() == null) {
            throw new IllegalArgumentException();
        }

        Result originalResult = complaintResponseRepresentingLock.getComplaint().getResult();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();

        return complaintResponseRepresentingLock.isCurrentlyLocked()
                && !(authorizationCheckService.isAtLeastInstructorForExercise(studentParticipation.getExercise()) || complaintResponseRepresentingLock.getReviewer().equals(user));
    }

    /**
     * Checks whether the reviewer is authorized to respond to this complaint, note: instructors are always allowed
     * to respond to complaints
     *
     * 1. Team Exercises
     * => The team tutor assesses the submissions and responds to complaints and more feedback requests
     *
     * 2. Individual Exercises
     * => Complaints can only be handled by a tutor who is not the original assessor
     * => Complaints of exam test runs can be assessed by instructors. They are identified by the same user being the assessor and student
     * => More feedback requests are handled by the assessor himself
     *
     * @param complaint Complaint for which to check
     * @param user      user who is trying to create a response to the complaint
     * @return true if the tutor is allowed to respond to the complaint, false otherwise
     */
    public boolean isUserAuthorizedToRespondToComplaint(Complaint complaint, User user) {
        if (user == null || complaint == null || complaint.getResult() == null) {
            throw new IllegalArgumentException();
        }

        Result originalResult = complaint.getResult();
        User assessor = originalResult.getAssessor();
        StudentParticipation participation = (StudentParticipation) originalResult.getParticipation();

        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorForExercise(participation.getExercise(), user);
        if (isAtLeastInstructor) {
            return true;
        }
        var isAtLeastTutor = authorizationCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);

        if (!isAtLeastTutor) {
            return false;
        }
        // for teams: the tutor who is responsible for team, should evaluate the complaint
        if (participation.getParticipant() instanceof Team team) {
            return user.getLogin().equals(team.getOwner().getLogin());
        }
        // for complaints, a different tutor should review the complaint
        else if (complaint.getComplaintType() == null || complaint.getComplaintType() == ComplaintType.COMPLAINT) {
            return assessor == null || !user.getLogin().equals(assessor.getLogin());
        }
        // for more feedback requests, the same tutor should review the request
        else if (complaint.getComplaintType() != null && complaint.getComplaintType() == ComplaintType.MORE_FEEDBACK) {
            return assessor == null || user.getLogin().equals(assessor.getLogin());
        }
        return false;
    }
}

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

    private static final String ENTITY_NAME = "complaintResponse";

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

    @Transactional // ok because of modifying query
    public ComplaintType removeLockOnComplaint(Long complaintId) {
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
        if (complaintFromDatabase.getComplaintResponse() == null) {
            throw new IllegalArgumentException("Complaint response does not exists for given complaint");
        }
        if (complaintFromDatabase.getComplaintResponse().getSubmittedTime() != null) {
            throw new IllegalArgumentException("Complaint response is already submitted");
        }

        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for removing the lock on the complaint");
        }
        // only instructors and the original reviewer can remove the lock while it is still running
        if (complaintFromDatabase.getComplaintResponse().isCurrentlyLocked() && !(authorizationCheckService.isAtLeastInstructorForExercise(studentParticipation.getExercise())
                || complaintFromDatabase.getComplaintResponse().getReviewer().equals(user))) {
            throw new ComplaintResponseLockedException(complaintFromDatabase.getComplaintResponse());
        }
        complaintResponseRepository.deleteById(complaintFromDatabase.getComplaintResponse().getId());

        return complaintFromDatabase.getComplaintType();
    }

    /**
     * Creates an initial complaint response for a given complaint
     * @param complaintId - complaint for which to create an initial response for
     * @return the persisted complaint response
     */
    @Transactional // ok because of modifying query
    public ComplaintResponse updateLockOnComplaint(Long complaintId) {
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
        if (complaintFromDatabase.getComplaintResponse() == null) {
            throw new IllegalArgumentException("Complaint response does not exists for given complaint");
        }
        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for updating the lock on the complaint");
        }
        // only instructors and the original reviewer can update the lock while it is still running
        if (complaintFromDatabase.getComplaintResponse().isCurrentlyLocked() && !(authorizationCheckService.isAtLeastInstructorForExercise(studentParticipation.getExercise())
                || complaintFromDatabase.getComplaintResponse().getReviewer().equals(user))) {
            throw new ComplaintResponseLockedException(complaintFromDatabase.getComplaintResponse());
        }

        // delete the old complaint response
        complaintResponseRepository.deleteById(complaintFromDatabase.getComplaintResponse().getId());
        complaintFromDatabase.setComplaintResponse(null);
        complaintResponseRepository.flush();

        ComplaintResponse newComplaintResponse = new ComplaintResponse();
        newComplaintResponse.setCreatedTime(ZonedDateTime.now());
        newComplaintResponse.setReviewer(user);
        newComplaintResponse.setComplaint(complaintFromDatabase);
        return complaintResponseRepository.save(newComplaintResponse);

    }

    /**
     * Creates an initial complaint response for a given complaint
     * @param complaintId - complaint for which to create an initial response for
     * @return the persisted complaint response
     */
    public ComplaintResponse createInitialComplaintResponse(Long complaintId) {
        if (complaintId == null) {
            throw new IllegalArgumentException("Complaint id should not be null");
        }
        Optional<Complaint> complaintFromDatabaseOptional = complaintRepository.findByIdWithEagerAssessor(complaintId);
        if (complaintFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("Complaint was not found in database");
        }
        Complaint complaintFromDatabase = complaintFromDatabaseOptional.get();
        if (complaintResponseRepository.findByComplaint_Id(complaintFromDatabase.getId()).isPresent()) {
            throw new IllegalArgumentException("Complaint response already exists for given complaint");
        }
        Result originalResult = complaintFromDatabase.getResult();
        User assessor = originalResult.getAssessor();
        User reviewer = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, complaintFromDatabase, assessor, reviewer)) {
            throw new AccessForbiddenException("Insufficient permission for creating the initial complaint response");
        }
        ComplaintResponse initialComplainResponse = new ComplaintResponse();
        initialComplainResponse.setCreatedTime(ZonedDateTime.now());
        initialComplainResponse.setReviewer(reviewer);
        initialComplainResponse.setComplaint(complaintFromDatabase);
        return complaintResponseRepository.save(initialComplainResponse);
    }

    /**
     * Updates an existing complaint response
     * @param updatedComplaintResponse - changed complaint response
     * @return updated complaint response
     */
    @Transactional // ok because of modifying query
    public ComplaintResponse updateComplaintResponse(ComplaintResponse updatedComplaintResponse) {
        if (updatedComplaintResponse.getId() == null) {
            throw new IllegalArgumentException("The complaint response needs to have an id");
        }
        Optional<ComplaintResponse> complaintResponseFromDatabaseOptional = complaintResponseRepository.findById(updatedComplaintResponse.getId());
        if (complaintResponseFromDatabaseOptional.isEmpty()) {
            throw new IllegalArgumentException("The complaint response was not found in the database");
        }
        ComplaintResponse complaintResponseFromDatabase = complaintResponseFromDatabaseOptional.get();
        Optional<Complaint> originalComplaintOptional = complaintRepository.findByIdWithEagerAssessor(complaintResponseFromDatabase.getComplaint().getId());
        if (originalComplaintOptional.isEmpty()) {
            throw new IllegalArgumentException("The complaint was not found in the database");
        }
        Complaint originalComplaint = originalComplaintOptional.get();
        if (originalComplaint.isAccepted() != null) {
            throw new IllegalArgumentException("You can not update the response to an already answered complaint");
        }

        Result originalResult = originalComplaint.getResult();
        User assessor = originalResult.getAssessor();
        User user = this.userService.getUser();
        StudentParticipation studentParticipation = (StudentParticipation) originalResult.getParticipation();
        if (!isUserAuthorizedToRespondToComplaint(studentParticipation, originalComplaint, assessor, user)) {
            throw new AccessForbiddenException("Insufficient permission for updating a complaint response");
        }
        // only instructors and the original reviewer can ignore the lock on a complaint response
        if (complaintResponseFromDatabase.isCurrentlyLocked()
                && !(authorizationCheckService.isAtLeastInstructorForExercise(studentParticipation.getExercise()) || complaintResponseFromDatabase.getReviewer().equals(user))) {
            throw new ComplaintResponseLockedException(complaintResponseFromDatabase);
        }

        originalComplaint.setAccepted(updatedComplaintResponse.getComplaint().isAccepted()); // accepted or denied
        originalComplaint = complaintRepository.save(originalComplaint);

        complaintResponseFromDatabase.setSubmittedTime(ZonedDateTime.now());
        complaintResponseFromDatabase.setResponseText(updatedComplaintResponse.getResponseText());
        complaintResponseFromDatabase.setComplaint(originalComplaint);
        complaintResponseFromDatabase.setReviewer(user);
        return complaintResponseRepository.save(complaintResponseFromDatabase);
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
            return assessor.equals(reviewer);
        }
        else if (complaint.getComplaintType() == null || complaint.getComplaintType().equals(ComplaintType.COMPLAINT)) {
            // if test run complaint
            if (complaint.getStudent() != null && complaint.getStudent().getLogin().equals(assessor.getLogin())
                    && authorizationCheckService.isAtLeastInstructorForExercise(participation.getExercise())) {
                return true;
            }
            return !assessor.equals(reviewer);
        }
        else if (complaint.getComplaintType() != null && complaint.getComplaintType().equals(ComplaintType.MORE_FEEDBACK)) {
            return assessor.equals(reviewer);
        }
        return false;
    }
}

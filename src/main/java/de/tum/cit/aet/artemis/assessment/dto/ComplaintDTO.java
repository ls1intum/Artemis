package de.tum.cit.aet.artemis.assessment.dto;

import java.time.ZonedDateTime;

import jakarta.annotation.Nullable;

import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.Complaint;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.programming.dto.ResultDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ComplaintDTO(long id, @Nullable String complaintText, boolean accepted, @NotNull ZonedDateTime submittedTime, @NotNull ComplaintType complaintType,
        @Nullable ComplaintResponseDTO complaintResponse, @NotNull ResultDTO result, @Nullable StudentDTO student, @Nullable TeamDTO team) {

    public static ComplaintDTO of(Complaint complaint) {
        if (complaint == null) {
            return null;
        }

        ComplaintResponseDTO complaintResponseDTO = complaint.getComplaintResponse() != null ? ComplaintResponseDTO.of(complaint.getComplaintResponse()) : null;

        ResultDTO resultDTO = ResultDTO.ofForComplaint(complaint.getResult());

        StudentDTO studentDTO = null;
        TeamDTO teamDTO = null;

        if (complaint.getStudent() != null && Hibernate.isInitialized(complaint.getStudent())) {
            studentDTO = new StudentDTO(complaint.getStudent());
        }
        else if (complaint.getTeam() != null && Hibernate.isInitialized(complaint.getTeam())) {
            Team team = complaint.getTeam();
            if (Hibernate.isInitialized(team.getStudents())) {
                teamDTO = new TeamDTO(team);
            }
            else {
                teamDTO = new TeamDTO(team.getId(), team.getName(), null, null);
            }
        }

        boolean accepted = complaint.isAccepted() != null ? complaint.isAccepted() : false;

        return new ComplaintDTO(complaint.getId(), complaint.getComplaintText(), accepted, complaint.getSubmittedTime(), complaint.getComplaintType(), complaintResponseDTO,
                resultDTO, studentDTO, teamDTO);
    }

    /**
     * Creates a new ComplaintDTO with all sensitive information filtered out
     */
    public ComplaintDTO withSensitiveInformationFiltered() {
        return new ComplaintDTO(this.id, this.complaintText, this.accepted, this.submittedTime, this.complaintType,
                this.complaintResponse != null ? this.complaintResponse.withSensitiveInformationFiltered() : null,
                this.result != null ? this.result.filterSensitiveInformation() : null, null, null);
    }

    /**
     * Creates a new ComplaintDTO with Result and ComplaintResponse sensitive information filtered out
     */
    public ComplaintDTO withResultAndComplaintResponseSensitiveInformationFiltered() {
        return new ComplaintDTO(this.id, this.complaintText, this.accepted, this.submittedTime, this.complaintType,
                this.complaintResponse != null ? this.complaintResponse.withSensitiveInformationFiltered() : null,
                this.result != null ? this.result.filterSensitiveInformation() : null, this.student, this.team);
    }
}

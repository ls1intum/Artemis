package de.tum.cit.aet.artemis.assessment.dto;

import java.util.Set;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.exercise.domain.Team;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamDTO(long id, @NotNull String name, @NotNull Set<StudentDTO> students, @NotNull StudentDTO owner) {

    public TeamDTO(Team team) {
        Set<StudentDTO> studentDTOs = team.getStudents().stream().map(StudentDTO::new).collect(Collectors.toSet());

        StudentDTO ownerDTO = team.getOwner() != null ? new StudentDTO(team.getOwner()) : null;

        this(team.getId(), team.getName(), studentDTOs, ownerDTO);
    }

}

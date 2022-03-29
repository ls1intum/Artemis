package de.tum.in.www1.artemis.service.dto;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;

/**
 * A DTO representing a team and all its linked properties.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TeamDTO extends AuditingEntityDTO {

    private Long id;

    private String name;

    private String shortName;

    private String image;

    private Exercise exercise;

    private Set<UserDTO> students = new HashSet<>();

    private UserDTO owner;

    /**
     * Jackson constructor
     */
    public TeamDTO() {
    }

    public TeamDTO(Team team) {
        this(team.getId(), team.getName(), team.getShortName(), team.getImage(), team.getExercise(), team.getStudents(), team.getOwner());
    }

    public TeamDTO(Long id, String name, String shortName, String image, Exercise exercise, Set<User> students, User owner) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.image = image;
        this.exercise = exercise;
        this.students = students.stream().map(UserDTO::new).collect(Collectors.toSet());
        this.owner = new UserDTO(owner);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Set<UserDTO> getStudents() {
        return students;
    }

    public void setStudents(Set<UserDTO> students) {
        this.students = students;
    }

    public UserDTO getOwner() {
        return owner;
    }

    public void setOwner(UserDTO owner) {
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

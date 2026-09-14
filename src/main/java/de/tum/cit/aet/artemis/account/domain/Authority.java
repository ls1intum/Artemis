package de.tum.cit.aet.artemis.account.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * An authority (a security role) used by Spring Security.
 */
@Entity
@Table(name = "jhi_authority")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Authority implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final Authority SUPER_ADMIN_AUTHORITY = new Authority(Role.SUPER_ADMIN.getAuthority());

    public static final Authority ADMIN_AUTHORITY = new Authority(Role.ADMIN.getAuthority());

    public static final Authority INSTRUCTOR_AUTHORITY = new Authority(Role.INSTRUCTOR.getAuthority());

    public static final Authority EDITOR_AUTHORITY = new Authority(Role.EDITOR.getAuthority());

    public static final Authority TA_AUTHORITY = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    public static final Authority USER_AUTHORITY = new Authority(Role.STUDENT.getAuthority());

    @NonNull
    @Size(max = 50)
    @Id
    @Column(length = 50)
    private String name;

    public Authority() {
        // empty constructor would not be available otherwise
    }

    /**
     * Builds an authority from its name alone, which is how a {@code UserDTO} carries a set of authorities: as a set
     * of plain strings rather than objects.
     * <p>
     * The mode has to be spelled out. Jackson 2 treated a lone String constructor as delegating by default; Jackson 3
     * reads the parameter name and would bind {@code "ROLE_USER"} as a property named {@code name} instead, so
     * deserializing {@code ["ROLE_USER"]} would fail with "no String-argument constructor/factory method".
     *
     * @param name the authority name, for example {@code ROLE_USER}
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public Authority(String name) {
        setName(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Authority authority = (Authority) obj;

        return Objects.equals(name, authority.name);
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Authority{" + "name='" + name + '\'' + "}";
    }
}

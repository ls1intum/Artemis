package de.tum.in.www1.artemis.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.security.Role;

/**
 * An authority (a security role) used by Spring Security.
 */
@Entity
@Table(name = "jhi_authority")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Authority implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static Authority ADMIN_AUTHORITY = new Authority(Role.ADMIN.getAuthority());

    public static Authority INSTRUCTOR_AUTHORITY = new Authority(Role.INSTRUCTOR.getAuthority());

    public static Authority EDITOR_AUTHORITY = new Authority(Role.EDITOR.getAuthority());

    public static Authority TA_AUTHORITY = new Authority(Role.TEACHING_ASSISTANT.getAuthority());

    public static Authority USER_AUTHORITY = new Authority(Role.STUDENT.getAuthority());

    @NotNull
    @Size(max = 50)
    @Id
    @Column(length = 50)
    private String name;

    public Authority() {
        // empty constructor would not be available otherwise
    }

    public Authority(String name) {
        // we need this constructor because we use the UserDTO which maps a set of authorities to a set of strings
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

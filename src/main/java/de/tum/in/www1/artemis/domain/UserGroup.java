package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.*;

@Entity
@Table(name = "user_groups")
public class UserGroup {

    @EmbeddedId
    private UserGroupKey id;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(name = "`groups`", insertable = false, updatable = false)
    private String group;

    @Embeddable
    public class UserGroupKey implements Serializable {

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "`groups`")
        private String group;
    }
}

package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

@Entity
@Table(name = "user_groups")
public class UserGroup {

    @EmbeddedId
    private UserGroupKey id;

    @Column(name = "`groups`", insertable = false, updatable = false)
    private String group;
}

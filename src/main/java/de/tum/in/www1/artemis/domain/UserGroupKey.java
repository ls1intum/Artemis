package de.tum.in.www1.artemis.domain;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class UserGroupKey implements Serializable {

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "`groups`")
    private String group;
}

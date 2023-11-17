package de.tum.in.www1.artemis.domain;

import javax.persistence.*;

@Entity
@Table(name = "artemis_version")
public class ArtemisVersion {

    @Id
    @Column(name = "latest_version", columnDefinition = "varchar(10)")
    private String latest_version;
}

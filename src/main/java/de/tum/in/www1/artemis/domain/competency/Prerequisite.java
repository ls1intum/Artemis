package de.tum.in.www1.artemis.domain.competency;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PREREQUISITE")
public class Prerequisite extends AbstractCompetency {
}

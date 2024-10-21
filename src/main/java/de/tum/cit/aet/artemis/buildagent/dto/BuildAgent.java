package de.tum.cit.aet.artemis.buildagent.dto;

import java.io.Serial;
import java.io.Serializable;

public record BuildAgent(String name, String memberAddress, String displayName) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}

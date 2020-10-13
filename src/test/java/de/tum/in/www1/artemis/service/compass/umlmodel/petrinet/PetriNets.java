package de.tum.in.www1.artemis.service.compass.umlmodel.petrinet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

class PetriNets {

    static final String PETRI_NET_MODEL_1A;

    static final String PETRI_NET_MODEL_1B;

    static final String PETRI_NET_MODEL_2;

    static {
        try {
            PETRI_NET_MODEL_1A = IOUtils.toString(PetriNets.class.getResource("petriNetModel1a.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_1B = IOUtils.toString(PetriNets.class.getResource("petriNetModel1b.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_2 = IOUtils.toString(PetriNets.class.getResource("petriNetModel2.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PetriNets() {
        // do not instantiate
    }
}

package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.petrinet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public class PetriNets {

    public static final String PETRI_NET_MODEL_1A;

    public static final String PETRI_NET_MODEL_1B;

    public static final String PETRI_NET_MODEL_2;

    public static final String PETRI_NET_MODEL_1A_V3;

    public static final String PETRI_NET_MODEL_1B_V3;

    public static final String PETRI_NET_MODEL_2_V3;

    static {
        try {
            PETRI_NET_MODEL_1A = IOUtils.toString(PetriNets.class.getResource("petriNetModel1a.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_1B = IOUtils.toString(PetriNets.class.getResource("petriNetModel1b.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_2 = IOUtils.toString(PetriNets.class.getResource("petriNetModel2.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_1A_V3 = IOUtils.toString(PetriNets.class.getResource("petriNetModel1av3.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_1B_V3 = IOUtils.toString(PetriNets.class.getResource("petriNetModel1bv3.json"), StandardCharsets.UTF_8);
            PETRI_NET_MODEL_2_V3 = IOUtils.toString(PetriNets.class.getResource("petriNetModel2v3.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PetriNets() {
        // do not instantiate
    }
}

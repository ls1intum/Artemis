package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Resolves the capability-adapter bundle for an exercise type — the standard Spring idiom: inject the list of
 * all {@link VariantTypeAdapters} beans, index by supported type. Adding a new exercise type means adding one
 * bean; nothing here changes.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantTypeRegistryService {

    private static final String ENTITY_NAME = "exerciseVariantGeneration";

    private final List<VariantTypeAdapters> adapterBundles;

    private final Map<ExerciseType, VariantTypeAdapters> adaptersByType = new EnumMap<>(ExerciseType.class);

    public VariantTypeRegistryService(List<VariantTypeAdapters> adapterBundles) {
        this.adapterBundles = adapterBundles;
    }

    /**
     * Indexes the injected bundles and fails fast on duplicate registrations for the same exercise type.
     */
    @PostConstruct
    public void init() {
        for (VariantTypeAdapters bundle : adapterBundles) {
            VariantTypeAdapters previous = adaptersByType.putIfAbsent(bundle.supportedExerciseType(), bundle);
            if (previous != null) {
                throw new IllegalStateException("Duplicate variant adapter bundle for exercise type " + bundle.supportedExerciseType() + ": " + previous.getClass().getSimpleName()
                        + " and " + bundle.getClass().getSimpleName());
            }
        }
    }

    /**
     * @param exerciseType the source exercise's type (read server-side, never client-supplied)
     * @return true when variant generation supports the type
     */
    public boolean isSupported(ExerciseType exerciseType) {
        return adaptersByType.containsKey(exerciseType);
    }

    /**
     * Whether a variant can be generated from this concrete exercise — the type must have a bundle AND the bundle
     * must accept the individual exercise (e.g. quizzes with drag-and-drop questions are rejected).
     *
     * @param exercise the source exercise (read server-side, never client-supplied)
     * @return true when variant generation supports the exercise
     */
    public boolean isSupported(Exercise exercise) {
        VariantTypeAdapters adapters = adaptersByType.get(exercise.getExerciseType());
        return adapters != null && adapters.supportsExercise(exercise);
    }

    /**
     * Resolves the adapter bundle for the given exercise type.
     *
     * @param exerciseType the source exercise's type
     * @return the adapter bundle
     * @throws BadRequestAlertException for unsupported types (translatable key)
     */
    public VariantTypeAdapters resolve(ExerciseType exerciseType) {
        VariantTypeAdapters adapters = adaptersByType.get(exerciseType);
        if (adapters == null) {
            throw new BadRequestAlertException("Variant generation is not supported for exercise type " + exerciseType, ENTITY_NAME, "unsupportedType");
        }
        return adapters;
    }
}

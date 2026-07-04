package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Resolves the capability-adapter bundle for an exercise type (plan Section 2.3).
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class VariantTypeRegistry {

    private final List<VariantTypeAdapters> adapterBundles;

    public VariantTypeRegistry(List<VariantTypeAdapters> adapterBundles) {
        this.adapterBundles = adapterBundles;
    }

    /**
     * Resolves the adapter bundle for the given exercise type.
     *
     * TODO (Sonnet): Implement per plan Section 2.3 / 5.1:
     * 1. Find the unique bundle whose supportedExerciseType() equals the given type.
     * 2. If none matches → throw BadRequestAlertException with a translatable error key
     * ("artemisApp.exerciseVariant.unsupportedType") so the resource returns 400 for unsupported types
     * (the client hides/disables the button per type anyway, Section 5.1).
     * 3. If more than one matches → fail fast at startup would be nicer: add a @PostConstruct duplicate check.
     *
     * TODO (Sonnet): Add `boolean isSupported(ExerciseType type)` for the resource's validation path.
     *
     * @param exerciseType the source exercise's type (read server-side, never client-supplied)
     * @return the adapter bundle
     */
    public VariantTypeAdapters resolve(ExerciseType exerciseType) {
        // TODO (Sonnet): implement — see method Javadoc.
        throw new UnsupportedOperationException("TODO (Sonnet): implement adapter resolution (plan Section 2.3)");
    }
}

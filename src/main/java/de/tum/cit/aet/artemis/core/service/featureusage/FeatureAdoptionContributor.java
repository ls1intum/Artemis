package de.tum.cit.aet.artemis.core.service.featureusage;

import java.util.List;

/**
 * Reports how widely the features of one module are configured.
 * <p>
 * Call counts answer how much a feature is used. They cannot tell a feature that is switched off everywhere from one that
 * is switched on and ignored, and those two need opposite decisions: the first may be worth promoting, the second may be
 * worth removing. Adoption counts supply the missing half.
 * <p>
 * Every module contributes its own, and the admin service simply collects all of them. That is not indirection for its own
 * sake: the admin module must not reach into another module's repositories, and a module that is switched off contributes
 * nothing automatically, because its beans are never created.
 * <p>
 * Implementations run when an admin opens the page, so they must stay cheap: aggregate counts only, no entity loading, and
 * a small fixed number of queries. There is deliberately no caching, since an admin page is not a hot path.
 */
public interface FeatureAdoptionContributor {

    /**
     * Returns the adoption counts of this module.
     *
     * @return one entry per reported feature, empty if the module has nothing to report
     */
    List<FeatureAdoptionEntry> collectAdoption();
}

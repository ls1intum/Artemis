import { MODULE_FEATURE_ATLAS, MODULE_FEATURE_IRIS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/**
 * The learner profile page composes sections served by two different modules, either of which may be disabled. These
 * helpers are the single definition of when each section can load, so the tab, the route guard and the page itself
 * cannot drift apart.
 *
 * The module lookups are deliberately separate from the predicates: a `computed()` may only read signals, so a
 * component reads the module state once into a signal rather than calling the profile service from inside a computed,
 * which would cache whatever the service happened to answer on the first evaluation.
 */

/**
 * Whether the atlas module — which serves the feedback and the course section, through the server-side
 * {@code LearnerProfileResource} and {@code CourseLearnerProfileResource} annotated with
 * {@code @Conditional(AtlasEnabled.class)} — is active. With atlas disabled those requests answer 404.
 *
 * @param profileService the profile service holding the active module features
 * @returns true if the atlas module is active
 */
export function isAtlasModuleActive(profileService: ProfileService): boolean {
    return profileService.isModuleFeatureActive(MODULE_FEATURE_ATLAS);
}

/**
 * Whether the iris module — which serves the insights section, through the server-side {@code IrisMemoryResource} —
 * is active.
 *
 * @param profileService the profile service holding the active module features
 * @returns true if the iris module is active
 */
export function isIrisModuleActive(profileService: ProfileService): boolean {
    return profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS);
}

/**
 * Whether the insights section can load. {@code IrisMemoryResource} is annotated with both
 * {@code @Conditional(IrisEnabled.class)} and {@code @FeatureToggle(Feature.Memiris)}, and
 * {@code FeatureToggleService} seeds the Memiris toggle independently of the module, so an admin can switch the toggle
 * on while iris is disabled. Both therefore have to hold.
 *
 * @param irisModuleActive whether the iris module is active
 * @param memirisEnabled whether the Memiris feature toggle is active
 * @returns true if the insights section can load
 */
export function insightsSectionAvailable(irisModuleActive: boolean, memirisEnabled: boolean): boolean {
    return irisModuleActive && memirisEnabled;
}

/**
 * Whether any section of the learner profile can load. When this is false the page would render empty, so the tab is
 * hidden and the route redirects instead.
 *
 * @param profileService the profile service holding the active module features
 * @param memirisEnabled whether the Memiris feature toggle is active
 * @returns true if at least one section can load
 */
export function learnerProfileAvailable(profileService: ProfileService, memirisEnabled: boolean): boolean {
    return isAtlasModuleActive(profileService) || insightsSectionAvailable(isIrisModuleActive(profileService), memirisEnabled);
}

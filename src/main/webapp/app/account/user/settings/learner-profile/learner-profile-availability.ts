import { MODULE_FEATURE_ATLAS } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

/**
 * The learner profile page is served by the atlas module, which may be disabled. These helpers are the single
 * definition of when the page can load, so the tab, the route guard and the page itself cannot drift apart.
 *
 * The module lookup is deliberately a function rather than a signal: a `computed()` may only read signals, so a
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
 * Whether any section of the learner profile can load. When this is false the page would render empty, so the tab is
 * hidden and the route redirects instead.
 *
 * @param profileService the profile service holding the active module features
 * @returns true if at least one section can load
 */
export function learnerProfileAvailable(profileService: ProfileService): boolean {
    return isAtlasModuleActive(profileService);
}

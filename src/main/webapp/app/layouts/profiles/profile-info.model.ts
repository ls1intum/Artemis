import { ActiveFeatureToggles } from 'app/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { AllowedOrionVersionRange } from 'app/orion/outdated-plugin-warning/orion-version-validator.service';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatureToggles;
    guidedTourMapping?: GuidedTourMapping;
    buildPlanURLTemplate: string;
    imprint: string;
    contact: string;
    allowedOrionVersions: AllowedOrionVersionRange;
}

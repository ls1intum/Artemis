import { ActiveFeatureToggles } from 'app/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';
import { AllowedOrionVersionRange } from 'app/intellij/outdated-plugin-warning/orion-outdated.component';

export type MajorMinorPatch = {
    major: number;
    minor: number;
    patch: number;
};

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatureToggles;
    guidedTourMapping?: GuidedTourMapping;
    allowedOrionVersions: AllowedOrionVersionRange;
}

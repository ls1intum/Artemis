import { ActiveFeatureToggles } from 'app/feature-toggle/feature-toggle.service';
import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatureToggles;
    guidedTourMapping?: GuidedTourMapping;
    buildPlanURLTemplate: string;
}

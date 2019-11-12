import { ActiveFeatures } from 'app/core/feature-toggle/feature-toggle.service';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    features: ActiveFeatures;
}

import { GuidedTourMapping } from 'app/guided-tour/guided-tour-setting.model';

export class ProfileInfo {
    activeProfiles: string[];
    ribbonEnv: string;
    inProduction: boolean;
    sentry?: { dsn: string };
    guidedTourMapping?: GuidedTourMapping;
}

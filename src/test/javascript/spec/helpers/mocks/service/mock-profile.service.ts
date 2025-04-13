import { PROFILE_DEV, PROFILE_PROD } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export class MockProfileService {
    public getProfileInfo = (): ProfileInfo =>
        ({
            programmingLanguageFeatures: [],
            activeProfiles: [],
            activeModuleFeatures: [],
            testServer: false,
        }) as unknown as ProfileInfo;

    public isProfileActive = (profile: string): boolean => this.getProfileInfo().activeProfiles?.includes(profile) ?? false;

    public isModuleFeatureActive = (feature: string): boolean => this.getProfileInfo().activeModuleFeatures?.includes(feature) ?? false;

    public isDevelopment = (): boolean => this.isProfileActive(PROFILE_DEV) ?? false;

    public isProduction = (): boolean => this.isProfileActive(PROFILE_PROD) ?? false;

    public isTestServer = (): boolean => this.getProfileInfo().testServer ?? false;
}

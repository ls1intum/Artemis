import { PROFILE_DEV, PROFILE_PROD } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export class MockProfileService {
    getProfileInfo = (): ProfileInfo =>
        ({
            programmingLanguageFeatures: [],
            activeProfiles: [],
            activeModuleFeatures: [],
            testServer: false,
        }) as unknown as ProfileInfo;

    isProfileActive = (profile: string): boolean => this.getProfileInfo().activeProfiles?.includes(profile) ?? false;

    isModuleFeatureActive = (feature: string): boolean => this.getProfileInfo().activeModuleFeatures?.includes(feature) ?? false;

    isDevelopment = (): boolean => this.isProfileActive(PROFILE_DEV) ?? false;

    isProduction = (): boolean => this.isProfileActive(PROFILE_PROD) ?? false;

    isTestServer = (): boolean => this.getProfileInfo().testServer ?? false;
}

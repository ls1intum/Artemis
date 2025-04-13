import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export class MockProfileService {
    getProfileInfo = (): ProfileInfo =>
        ({
            activeProfiles: [],
            activeModuleFeatures: [],
            testServer: false,
        }) as unknown as ProfileInfo;

    isProfileActive = (profile: string): boolean => this.getProfileInfo().activeProfiles?.includes(profile) ?? false;

    isModuleFeatureActive = (feature: string): boolean => this.getProfileInfo().activeModuleFeatures?.includes(feature) ?? false;

    isDevelopment = (): boolean => this.isProfileActive('dev') ?? false;

    isProduction = (): boolean => this.isProfileActive('prod') ?? false;

    isTestServer = (): boolean => this.getProfileInfo().testServer ?? false;
}

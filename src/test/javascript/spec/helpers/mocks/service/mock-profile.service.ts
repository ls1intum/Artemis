import { PROFILE_DEV, PROFILE_PROD } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export class MockProfileService {
    private profileInfo: ProfileInfo = {
        programmingLanguageFeatures: [],
        activeProfiles: [],
        activeModuleFeatures: [],
        testServer: false,
    };

    // setters for tests
    public setActiveProfiles(profiles: string[]) {
        this.profileInfo.activeProfiles = profiles;
    }
    public setActiveModuleFeatures(...features: string[]) {
        this.profileInfo.activeModuleFeatures = features;
    }
    public setTestServer(flag = true) {
        this.profileInfo.testServer = flag;
    }

    // real API
    public getProfileInfo(): ProfileInfo {
        return this.profileInfo;
    }

    public isProfileActive = (profile: string): boolean => this.getProfileInfo().activeProfiles?.includes(profile) ?? false;

    public isModuleFeatureActive = (feature: string): boolean => this.getProfileInfo().activeModuleFeatures?.includes(feature) ?? false;

    public isDevelopment = (): boolean => this.isProfileActive(PROFILE_DEV) ?? false;

    public isProduction = (): boolean => this.isProfileActive(PROFILE_PROD) ?? false;

    public isTestServer = (): boolean => this.getProfileInfo().testServer ?? false;
}

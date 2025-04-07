import { BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export class MockProfileService {
    getProfileInfo = () =>
        new BehaviorSubject<ProfileInfo | undefined>({
            activeProfiles: [],
            activeModuleFeatures: [],
        } as unknown as ProfileInfo);
    isProfileActive: (profile: string) => boolean = () => false;
    isFeatureActive: (feature: string) => boolean = () => false;
}

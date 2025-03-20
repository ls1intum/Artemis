import { BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/core/layouts/profiles/shared/profile-info.model';

export class MockProfileService {
    getProfileInfo = () =>
        new BehaviorSubject<ProfileInfo | undefined>({
            activeProfiles: [],
        } as unknown as ProfileInfo);
}

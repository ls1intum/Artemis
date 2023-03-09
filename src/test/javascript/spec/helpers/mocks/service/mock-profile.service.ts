import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { BehaviorSubject } from 'rxjs';

export class MockProfileService {
    getProfileInfo = () => new BehaviorSubject<ProfileInfo | undefined>(undefined);
}

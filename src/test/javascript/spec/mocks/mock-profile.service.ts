import { BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/layouts/profiles/profile-info.model';

export class MockProfileService {
    getProfileInfo = () => new BehaviorSubject<ProfileInfo | null>(null);
}

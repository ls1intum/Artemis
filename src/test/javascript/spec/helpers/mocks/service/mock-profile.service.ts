import { Subject } from 'rxjs';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';

export class MockProfileService {
    getProfileInfo = () => new Subject<ProfileInfo>();
}

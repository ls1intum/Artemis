import { BehaviorSubject } from 'rxjs';
import { ProfileInfo } from 'app/layouts';

export class MockProfileService {
    getProfileInfo = () => new BehaviorSubject<ProfileInfo | null>(null);
}

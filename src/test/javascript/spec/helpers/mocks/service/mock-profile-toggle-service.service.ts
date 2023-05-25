import { Observable, of } from 'rxjs';
import { ProfileToggle } from 'app/shared/profile-toggle/profile-toggle.service';

export class MockProfileToggleServiceService {
    getProfileToggleActive(profile: ProfileToggle): Observable<boolean> {
        return of(true);
    }
}

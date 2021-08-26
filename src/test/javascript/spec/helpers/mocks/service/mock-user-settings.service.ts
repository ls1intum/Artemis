import { Subject } from 'rxjs';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';

export class MockUserSettingsService {
    private applyNewChangesSource = new Subject<string>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();

    loadUserOptions = (category: UserSettingsCategory) => {};

    sendApplyChangesEvent(message: string): void {
        this.applyNewChangesSource.next(message);
    }
}

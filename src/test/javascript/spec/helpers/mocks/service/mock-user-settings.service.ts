import { Subject } from 'rxjs';
import { UserSettingsCategory } from 'app/shared/constants/user-settings.constants';

export class MockUserSettingsService {
    private applyNewChangesSource = new Subject<string>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();

    loadUserOptions = () => {};
    loadUserOptionCoresSuccessAsSettings = () => {};
    saveUserOptions = () => {};
    saveUserOptionsSuccess = () => {};
    extractOptionCoresFromSettings = () => {};

    sendApplyChangesEvent(message: string): void {
        this.applyNewChangesSource.next(message);
    }
}

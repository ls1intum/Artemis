import { Subject } from 'rxjs';
import { NotificationSetting } from 'app/shared/user-settings/notification-settings/notification-settings-structure';

export class MockUserSettingsService {
    private applyNewChangesSource = new Subject<string>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();

    loadSettings = () => {};
    loadSettingsSuccessAsSettingsStructure = () => {};
    loadSettingsSuccessAsIndividualSettings = () => [] as NotificationSetting[];
    saveSettings = () => {};
    saveSettingsSuccess = () => {};
    extractIndividualSettingsFromSettingsStructure = () => {};

    sendApplyChangesEvent(message: string): void {
        this.applyNewChangesSource.next(message);
    }
}

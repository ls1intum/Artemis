import { Subject } from 'rxjs';

export class MockUserSettingsService {
    private applyNewChangesSource = new Subject<string>();
    userSettingsChangeEvent = this.applyNewChangesSource.asObservable();

    loadSettings = () => {};
    loadSettingsSuccessAsSettingsStructure = () => {};
    saveSettings = () => {};
    saveSettingsSuccess = () => {};
    extractIndividualSettingsFromSettingsStructure = () => {};

    sendApplyChangesEvent(message: string): void {
        this.applyNewChangesSource.next(message);
    }
}

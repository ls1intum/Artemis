import { Alert } from 'app/foundation/service/alert.service';

export class MockAlertService {
    success = (message: string) => ({}) as Alert;
    error = (message: string) => ({}) as Alert;
    warning = (message: string) => ({}) as Alert;
    info = (message: string) => ({}) as Alert;
    addAlert = (alert: Alert) => {};
    addErrorAlert = (message?: any, translationKey?: string, translationParams?: { [key: string]: unknown }) => {};
    /** Whether the global handling would alert for the error; nothing is alerted unless a test says so. */
    showsHttpErrorAlert = (response: unknown) => false;
    closeAll = () => {};
    get = () => [] as Alert[];
}

import { Alert } from 'app/shared/service/alert.service';

export class MockAlertService {
    success = (message: string) => ({}) as Alert;
    error = (message: string) => ({}) as Alert;
    addAlert = (alert: Alert) => {};
    addErrorAlert = (message?: any, translationKey?: string, translationParams?: { [key: string]: unknown }) => {};
}

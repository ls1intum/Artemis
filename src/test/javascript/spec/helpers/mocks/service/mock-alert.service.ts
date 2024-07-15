import { Alert, AlertCreationProperties } from 'app/core/util/alert.service';

export class MockAlertService {
    success = (message: string) => ({}) as Alert;
    error = (message: string) => ({}) as Alert;
    addAlert = (alert: AlertCreationProperties) => ({}) as Alert;
}

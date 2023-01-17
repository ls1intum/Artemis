import { Alert } from 'app/core/util/alert.service';

export class MockAlertService {
    success = () => ({} as Alert);
}

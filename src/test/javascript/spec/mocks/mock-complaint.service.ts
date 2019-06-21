import { of } from 'rxjs';

export class MockComplaintService {
    getForTutor = (exerciseId: number) => of();
}

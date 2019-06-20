import { EntityResponseType, IComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint';
import { User } from 'app/core';
import { Result } from 'app/entities/result';
import { of, Observable } from 'rxjs';

export const MockComplaintResponse: any = {
    body: {
        complaintType: ComplaintType.COMPLAINT,
        accepted: undefined,
        complaintText: 'I think my answer was better than 2',
        id: 123,
        result: new Result(),
        resultBeforeComplaint: '',
        student: new User(),
    },
};

export class MockComplaintService implements IComplaintService {
    create(complaint: Complaint): Observable<EntityResponseType> {
        return of();
    }
    find(id: number): Observable<EntityResponseType> {
        return of();
    }
    findByResultId(resultId: number): Observable<EntityResponseType> {
        return of(MockComplaintResponse);
    }
    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return of(3);
    }
}

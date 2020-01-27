import { EntityResponseType, IComplaintService } from 'app/entities/complaint/complaint.service';
import { Complaint, ComplaintType } from 'app/entities/complaint';
import { User } from 'app/core/user/user.model';
import { Result } from 'app/entities/result';
import { Observable, of } from 'rxjs';

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

export const MockComplaintResponse2: any = {
    body: {
        complaintType: ComplaintType.MORE_FEEDBACK,
        accepted: true,
        complaintText: 'I think my answer was better than 2',
        id: 111,
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
        if (resultId == 111) {
            return of(MockComplaintResponse2);
        }
        return of(MockComplaintResponse);
    }
    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return of(3);
    }
}

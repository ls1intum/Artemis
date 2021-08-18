import { EntityResponseType, IComplaintService } from 'app/complaints/complaint.service';
import { User } from 'app/core/user/user.model';
import { Result } from 'app/entities/result.model';
import { Observable, of } from 'rxjs';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';

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
    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        if (submissionId === 111) {
            return of(MockComplaintResponse2);
        }
        return of(MockComplaintResponse);
    }
    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return of(3);
    }
}

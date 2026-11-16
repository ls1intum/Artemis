import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Observable, of } from 'rxjs';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import type { Complaint as ComplaintDTO } from 'app/openapi/model/complaint';
import type { ComplaintsForTutorQuery } from 'app/openapi/model/complaints-for-tutor-query';
import { ComplaintListQuery } from 'app/assessment/shared/services/complaint.service';

const complaintObject: ComplaintDTO = {
    complaintType: 'COMPLAINT',
    complaintText: 'I think my answer was better than 2',
    id: 123,
    result: {},
    participant: { id: 1, name: 'Student One', login: 'student1', isStudent: true },
};
const feedbackRequestObject: ComplaintDTO = {
    complaintType: 'MORE_FEEDBACK',
    complaintIsAccepted: true,
    complaintText: 'I think my answer was better than 2',
    id: 111,
    result: {},
    participant: { id: 1, name: 'Student One', login: 'student1', isStudent: true },
};

function toComplaint(dto: ComplaintDTO): Complaint {
    return Object.assign(new Complaint(), {
        id: dto.id,
        complaintText: dto.complaintText,
        complaintType: dto.complaintType === 'MORE_FEEDBACK' ? ComplaintType.MORE_FEEDBACK : ComplaintType.COMPLAINT,
        accepted: dto.complaintIsAccepted,
    });
}

export class MockComplaintService {
    findBySubmissionId(submissionId: number): Observable<ComplaintDTO | undefined> {
        return of(submissionId === 111 ? feedbackRequestObject : complaintObject);
    }

    findBySubmissionIdForAssessment(submissionId: number): Observable<ComplaintDTO | undefined> {
        return this.findBySubmissionId(submissionId);
    }

    findAll(query: ComplaintListQuery): Observable<Complaint[]> {
        return of([complaintObject, feedbackRequestObject].map(toComplaint));
    }

    findAllForTutor(query: ComplaintsForTutorQuery): Observable<Complaint[]> {
        return of([complaintObject, feedbackRequestObject].map(toComplaint));
    }

    isComplaintLocked(complaint: Complaint): boolean | undefined {
        return undefined;
    }

    isComplaintLockedByLoggedInUser(complaint: Complaint): boolean | undefined {
        return undefined;
    }

    isComplaintLockedForLoggedInUser(complaint: Complaint, exercise: Exercise): boolean | undefined {
        return undefined;
    }

    convertComplaintFromServer(dto: ComplaintDTO, result?: Result): Complaint {
        return Object.assign(toComplaint(dto), { result });
    }
}

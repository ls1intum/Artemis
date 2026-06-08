import { EntityResponseType, EntityResponseTypeArray, IComplaintService } from 'app/assessment/shared/services/complaint.service';
import { Result, ResultSimpleDTO } from 'app/exercise/shared/entities/result/result.model';
import { Observable, of } from 'rxjs';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HttpResponse } from '@angular/common/http';
import { ComplaintDTO, ParticipantDTO } from 'app/assessment/shared/entities/complaint-dto.model';

const studentParticipant: ParticipantDTO = { id: 1, name: 'Student One', login: 'student1', isStudent: true };

const complaintObject: ComplaintDTO = {
    complaintType: ComplaintType.COMPLAINT,
    complaintIsAccepted: undefined,
    complaintText: 'I think my answer was better than 2',
    id: 123,
    result: new ResultSimpleDTO(),
    participant: studentParticipant,
};
const feedbackRequestObject: ComplaintDTO = {
    complaintType: ComplaintType.MORE_FEEDBACK,
    complaintIsAccepted: true,
    complaintText: 'I think my answer was better than 2',
    id: 111,
    result: new ResultSimpleDTO(),
    participant: studentParticipant,
};

export const MockComplaintResponse: HttpResponse<ComplaintDTO> = {
    body: complaintObject,
} as HttpResponse<ComplaintDTO>;

export const MockComplaintResponse2: HttpResponse<ComplaintDTO> = {
    body: feedbackRequestObject,
} as HttpResponse<ComplaintDTO>;

export const MockComplaintArrayResponse: HttpResponse<ComplaintDTO[]> = {
    body: [complaintObject, feedbackRequestObject],
} as HttpResponse<ComplaintDTO[]>;

export class MockComplaintService implements IComplaintService {
    create(complaint: Complaint): Observable<EntityResponseType> {
        return of();
    }
    find(complaintId: number): Observable<EntityResponseType> {
        return of();
    }
    findBySubmissionId(submissionId: number): Observable<EntityResponseType> {
        if (submissionId === 111) {
            return of(MockComplaintResponse2);
        }
        return of(MockComplaintResponse);
    }

    findAllByCourseId(courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
    }

    findAllByCourseIdAndExamId(courseId: number, examId: number): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
    }

    findAllByExerciseId(exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
    }

    findAllByTutorIdForCourseId(tutorId: number, courseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
    }

    findAllByTutorIdForExerciseId(tutorId: number, exerciseId: number, complaintType: ComplaintType): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
    }

    getComplaintsForTestRun(exerciseId: number): Observable<EntityResponseTypeArray> {
        return of(MockComplaintArrayResponse);
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
    // Some specs feed Complaint-shaped fixtures (with `accepted`) instead of a ComplaintDTO (with `complaintIsAccepted`),
    // so we fall back to the entity field to keep those tests working with this drop-in mock.
    convertComplaintFromServerInList(dto: ComplaintDTO): Complaint {
        return Object.assign(new Complaint(), dto, {
            accepted: dto.complaintIsAccepted ?? (dto as Complaint).accepted,
        });
    }
    convertComplaintFromServer(dto: ComplaintDTO, result?: Result): Complaint {
        return Object.assign(new Complaint(), dto, {
            accepted: dto.complaintIsAccepted ?? (dto as Complaint).accepted,
            result,
        });
    }
}

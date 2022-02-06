import { EntityResponseType, EntityResponseTypeArray, IComplaintService } from 'app/complaints/complaint.service';
import { User } from 'app/core/user/user.model';
import { Result } from 'app/entities/result.model';
import { Observable, of } from 'rxjs';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { Exercise } from 'app/entities/exercise.model';
import { HttpResponse } from '@angular/common/http';

const complaintObject: Complaint = {
    complaintType: ComplaintType.COMPLAINT,
    accepted: undefined,
    complaintText: 'I think my answer was better than 2',
    id: 123,
    result: new Result(),
    student: new User(),
};
const feedbackRequestObject: Complaint = {
    complaintType: ComplaintType.MORE_FEEDBACK,
    accepted: true,
    complaintText: 'I think my answer was better than 2',
    id: 111,
    result: new Result(),
    student: new User(),
};

export const MockComplaintResponse: HttpResponse<Complaint> = {
    body: complaintObject,
} as HttpResponse<Complaint>;

export const MockComplaintResponse2: HttpResponse<Complaint> = {
    body: feedbackRequestObject,
} as HttpResponse<Complaint>;

export const MockComplaintArrayResponse: HttpResponse<Complaint[]> = {
    body: [complaintObject, feedbackRequestObject],
} as HttpResponse<Complaint[]>;

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
    getNumberOfAllowedComplaintsInCourse(courseId: number): Observable<number> {
        return of(3);
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

    getMoreFeedbackRequestsForTutor(exerciseId: number): Observable<EntityResponseTypeArray> {
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
}

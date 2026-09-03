import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { UserStoryEffort } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';

/** One started user story with the effort reported for it (mirrors the backend {@code UserStoryEffortStatusDTO}). */
export interface UserStoryEffortStatus extends UserStoryEffort {
    exerciseId: number;
}

/**
 * Reads and writes the effort the current user reports for a user story exercise (mirrors the backend
 * {@code UserStoryEffortResource}).
 *
 * Both endpoints act on the caller's own participation only. A tutor reads the pair off the participation while
 * assessing, so there is nothing here for them.
 */
@Injectable({ providedIn: 'root' })
export class UserStoryEffortService {
    private readonly http = inject(HttpClient);

    private resourceUrl(exerciseId: number): string {
        return `api/programming/user-story-exercises/${exerciseId}/effort`;
    }

    /** The pair the current user has reported, with unset values omitted by the server. */
    getEffort(exerciseId: number): Observable<UserStoryEffort> {
        return this.http.get<UserStoryEffort>(this.resourceUrl(exerciseId));
    }

    /**
     * Every user story in the course the current user has started, with whatever effort they reported. One request for
     * the whole overview - the pair is not serialized with each participation, because an inverse association there cost
     * a query per participation and broke the dashboard payload.
     */
    getEffortsForCourse(courseId: number): Observable<UserStoryEffortStatus[]> {
        return this.http.get<UserStoryEffortStatus[]>(`api/programming/courses/${courseId}/user-story-efforts`);
    }

    /** The pair reported on one participation, for the tutor assessing it. */
    getEffortForParticipation(participationId: number): Observable<UserStoryEffort> {
        return this.http.get<UserStoryEffort>(`api/programming/participations/${participationId}/user-story-effort`);
    }

    /** Records the pair, replacing anything reported before. Rejected by the server once the story is due. */
    updateEffort(exerciseId: number, effort: UserStoryEffort): Observable<UserStoryEffort> {
        return this.http.put<UserStoryEffort>(this.resourceUrl(exerciseId), effort);
    }
}

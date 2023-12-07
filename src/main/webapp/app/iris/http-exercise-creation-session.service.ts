import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisCourseHttpSessionService } from 'app/iris/http-course-session.service';

/**
 * The `IrisHttpExerciseCreationSessionService` provides methods for retrieving existing or creating new Iris exercise creation sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpExerciseCreationSessionService extends IrisCourseHttpSessionService {
    constructor(http: HttpClient) {
        super(http, 'exercise-creation-sessions');
    }
}

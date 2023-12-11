import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisExerciseHttpSessionService } from 'app/iris/http-exercise-session.service';
import { Response } from 'app/iris/http-session.service';
import { IrisSession } from 'app/entities/iris/iris-session.model';

/**
 * The `IrisHttpCodeEditorSessionService` provides methods for retrieving existing or creating new Iris sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorSessionService extends IrisExerciseHttpSessionService {
    constructor(http: HttpClient) {
        super(http, 'code-editor-sessions');
    }

    kickstartSession(exerciseId: number): Response<IrisSession> {
        return this.http.post<never>(`${this.apiPrefix}/programming-exercises/${exerciseId}/${this.sessionType}/kickstart`, null);
    }
}

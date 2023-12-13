import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { IrisExerciseHttpSessionService } from 'app/iris/http-exercise-session.service';

/**
 * The `IrisHttpCodeEditorSessionService` provides methods for retrieving existing or creating new Iris sessions.
 * It interacts with the server-side API to perform session-related operations.
 */
@Injectable({ providedIn: 'root' })
export class IrisHttpCodeEditorSessionService extends IrisExerciseHttpSessionService {
    constructor(http: HttpClient) {
        super(http, 'code-editor-sessions');
    }
}

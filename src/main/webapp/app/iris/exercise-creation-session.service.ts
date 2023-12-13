import { Injectable } from '@angular/core';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisSessionService } from 'app/iris/session.service';
import { IrisHttpExerciseCreationSessionService } from 'app/iris/http-exercise-creation-session.service';
import { IrisHttpMessageService } from 'app/iris/http-message.service';

/**
 * The IrisChatSessionService is responsible for managing Iris chat sessions and retrieving their associated messages.
 */
@Injectable()
export class IrisExerciseCreationSessionService extends IrisSessionService {
    /**
     * Uses the IrisHttpChatSessionService and IrisHttpChatMessageService to retrieve and manage Iris chat sessions.
     */
    constructor(stateStore: IrisStateStore, irisSessionService: IrisHttpExerciseCreationSessionService, irisHttpMessageService: IrisHttpMessageService) {
        super(stateStore, irisSessionService, irisHttpMessageService);
    }
}

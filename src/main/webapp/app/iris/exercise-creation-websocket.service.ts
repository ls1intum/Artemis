import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisRateLimitInformation, IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { Observable, Subject } from 'rxjs';

/**
 * The IrisExerciseCreationWebsocketMessageType defines the type of message sent over the exercise creation websocket.
 */
export enum IrisExerciseCreationWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    ERROR = 'ERROR',
}

export type ExerciseUpdate = {
    problemStatement: string;
    exerciseUpdate: ExerciseMetadata;
};

export type ExerciseMetadata = {
    title: string;
    short_name: string;
    categories: string[];
    difficulty: 'no level' | 'easy' | 'medium' | 'hard';
    participation: 'individual' | 'team';
    allow_offline_IDE: boolean;
    allow_online_editor: boolean;
    publish_build_plan: boolean;
    programming_language: 'java' | 'python' | 'c' | 'haskell' | 'kotlin' | 'vhdl' | 'assembler' | 'swift' | 'ocaml' | 'empty';
    include_in_course_score: 'yes' | 'bonus' | 'no';
    points: number;
    bonus_points: number;
    submission_policy: 'none' | 'lock repository' | 'submission penalty';
};

/**
 * The IrisCodeEditorWebsocketDTO is the data transfer object for messages sent over the exercise creation websocket.
 * It is either a message type or an error type.
 * If it is a message type, it contains an IrisMessage. It might also contain an exercise update.
 * If it is an error type, it contains an error message.
 */
export class IrisExerciseCreationWebsocketDTO {
    type: IrisExerciseCreationWebsocketMessageType;
    message?: IrisMessage;
    exerciseUpdate?: ExerciseUpdate;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
    rateLimitInfo?: IrisRateLimitInformation;
}

/**
 * The IrisCodeEditorWebsocketService handles the websocket communication for receiving messages in the code editor channels.
 */
@Injectable()
export class IrisExerciseCreationWebsocketService extends IrisWebsocketService {
    private exerciseUpdates: Subject<ExerciseUpdate> = new Subject<ExerciseUpdate>();

    /**
     * Creates an instance of IrisExerciseCreationWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(jhiWebsocketService: JhiWebsocketService, stateStore: IrisStateStore) {
        super(jhiWebsocketService, stateStore, 'exercise-creation-sessions');
    }

    protected handleWebsocketResponse(response: IrisExerciseCreationWebsocketDTO): void {
        if (response.rateLimitInfo) {
            super.handleRateLimitInfo(response.rateLimitInfo);
        }
        console.log('Received websocket message:');
        console.dir(response);
        switch (response.type) {
            case IrisExerciseCreationWebsocketMessageType.MESSAGE:
                super.handleMessage(response.message);
                if (response.exerciseUpdate) {
                    this.exerciseUpdates.next(response.exerciseUpdate);
                }
                break;
            case IrisExerciseCreationWebsocketMessageType.ERROR:
                super.handleError(response.errorTranslationKey, response.translationParams);
                break;
        }
    }

    /**
     * Returns a subject that notifies subscribers of an update to the exercise being created.
     * @returns {Subject<ExerciseUpdate>}
     */
    public onExerciseUpdate(): Observable<ExerciseUpdate> {
        return this.exerciseUpdates.asObservable();
    }
}

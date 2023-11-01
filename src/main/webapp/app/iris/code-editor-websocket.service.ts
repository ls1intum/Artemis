import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisRateLimitInformation, IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { Observable, Subject } from 'rxjs';
import { ExerciseComponent } from 'app/entities/iris/iris-content-type.model';

/**
 * The IrisCodeEditorWebsocketMessageType defines the type of message sent over the code editor websocket.
 */
enum IrisCodeEditorWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    STEP_SUCCESS = 'STEP_SUCCESS',
    STEP_EXCEPTION = 'STEP_EXCEPTION',
    ERROR = 'ERROR',
}

export class StepExecutionSuccess {
    messageId: number;
    planId: number;
    stepId: number;
    component: ExerciseComponent;
    paths?: string[];
    updatedProblemStatement?: string;
}

export class StepExecutionException {
    messageId: number;
    planId: number;
    stepId: number;
    errorMessage?: string;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
}

/**
 * The IrisCodeEditorWebsocketDTO is the data transfer object for messages sent over the code editor websocket.
 * It either contains an IrisMessage or a map of changes or an error message.
 */
export class IrisCodeEditorWebsocketDTO {
    type: IrisCodeEditorWebsocketMessageType;
    message?: IrisMessage;
    stepExecutionSuccess?: StepExecutionSuccess;
    stepExecutionException?: StepExecutionException;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
    rateLimitInfo?: IrisRateLimitInformation;
}

/**
 * The IrisCodeEditorWebsocketService handles the websocket communication for receiving messages in the code editor channels.
 */
@Injectable()
export class IrisCodeEditorWebsocketService extends IrisWebsocketService {
    private stepSuccess: Subject<StepExecutionSuccess> = new Subject<StepExecutionSuccess>();
    private stepException: Subject<StepExecutionException> = new Subject<StepExecutionException>();

    /**
     * Creates an instance of IrisCodeEditorWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(jhiWebsocketService: JhiWebsocketService, stateStore: IrisStateStore) {
        super(jhiWebsocketService, stateStore, 'code-editor-sessions');
    }

    protected handleWebsocketResponse(response: IrisCodeEditorWebsocketDTO): void {
        console.log(response);
        if (response.rateLimitInfo) {
            this.handleRateLimitInfo(response.rateLimitInfo);
        }
        switch (response.type) {
            case IrisCodeEditorWebsocketMessageType.MESSAGE:
                super.handleMessage(response.message);
                break;
            case IrisCodeEditorWebsocketMessageType.STEP_SUCCESS:
                this.handleStepSuccess(response.stepExecutionSuccess!);
                break;
            case IrisCodeEditorWebsocketMessageType.STEP_EXCEPTION:
                this.handleExecutionException(response.stepExecutionException!);
                break;
            case IrisCodeEditorWebsocketMessageType.ERROR:
                super.handleError(response.errorTranslationKey, response.translationParams);
                break;
        }
    }

    private handleStepSuccess(response: StepExecutionSuccess): void {
        this.stepSuccess.next(response); // notify subscribers of changes applied
    }

    private handleExecutionException(response: StepExecutionException): void {
        this.stepException.next(response);
    }

    /**
     * Returns a subject that notifies subscribers when the code editor should be reloaded.
     * @returns {Subject<StepExecutionSuccess>}
     */
    public onPromptReload(): Observable<StepExecutionSuccess> {
        return this.stepSuccess.asObservable();
    }

    /**
     * Returns a subject that notifies subscribers when the step execution failed.
     * @returns {Subject<StepExecutionException>}
     */
    public onStepException(): Observable<StepExecutionException> {
        return this.stepException.asObservable();
    }
}

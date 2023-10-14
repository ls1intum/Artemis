import { Injectable } from '@angular/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { IrisStateStore } from 'app/iris/state-store.service';
import { IrisWebsocketService } from 'app/iris/websocket.service';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { IrisErrorMessageKey } from 'app/entities/iris/iris-errors.model';
import { Observable, Subject } from 'rxjs';

/**
 * The IrisCodeEditorWebsocketMessageType defines the type of message sent over the code editor websocket.
 */
enum IrisCodeEditorWebsocketMessageType {
    MESSAGE = 'MESSAGE',
    CODE_CHANGES = 'CODE_CHANGES',
    ERROR = 'ERROR',
}

enum FileChangeType {
    CREATE = 'CREATE',
    DELETE = 'DELETE',
    RENAME = 'RENAME',
    MODIFY = 'MODIFY',
}

class FileChange {
    type: FileChangeType;
    file: string;
    original?: string;
    updated?: string;
}

export enum IrisExerciseComponent {
    PROBLEM_STATEMENT = 'problemStatement',
    SOLUTION_REPOSITORY = 'solutionRepository',
    TEMPLATE_REPOSITORY = 'templateRepository',
    TEST_REPOSITORY = 'testRepository',
}

export class IrisExerciseComponentChangeSet {
    component: IrisExerciseComponent;
    changes: FileChange[]; //
}

/**
 * The IrisCodeEditorWebsocketDTO is the data transfer object for messages sent over the code editor websocket.
 * It either contains an IrisMessage or a map of changes or an error message.
 */
export class IrisCodeEditorWebsocketDTO {
    type: IrisCodeEditorWebsocketMessageType;
    message?: IrisMessage;
    changes?: IrisExerciseComponentChangeSet;
    errorTranslationKey?: IrisErrorMessageKey;
    translationParams?: Map<string, any>;
}

/**
 * The IrisCodeEditorWebsocketService handles the websocket communication for receiving messages in the code editor channels.
 */
@Injectable()
export class IrisCodeEditorWebsocketService extends IrisWebsocketService {
    private subject: Subject<IrisExerciseComponentChangeSet> = new Subject();

    /**
     * Creates an instance of IrisCodeEditorWebsocketService.
     * @param jhiWebsocketService The JhiWebsocketService for websocket communication.
     * @param stateStore The IrisStateStore for managing the state of the application.
     */
    constructor(jhiWebsocketService: JhiWebsocketService, stateStore: IrisStateStore) {
        super(jhiWebsocketService, stateStore, 'code-editor-sessions');
    }

    protected handleWebsocketResponse(response: IrisCodeEditorWebsocketDTO): void {
        switch (response.type) {
            case IrisCodeEditorWebsocketMessageType.MESSAGE:
                super.handleMessage(response.message);
                break;
            case IrisCodeEditorWebsocketMessageType.CODE_CHANGES:
                this.handleChanges(response.changes);
                break;
            case IrisCodeEditorWebsocketMessageType.ERROR:
                super.handleError(response.errorTranslationKey, response.translationParams);
                break;
        }
    }

    protected handleChanges(changes?: IrisExerciseComponentChangeSet) {
        if (changes) this.subject.next(changes);
    }

    public onCodeChanges(): Observable<IrisExerciseComponentChangeSet> {
        return this.subject.asObservable();
    }
}

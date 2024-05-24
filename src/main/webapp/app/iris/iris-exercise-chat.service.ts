import { Injectable } from '@angular/core';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { IrisChatService } from 'app/iris/iris-chat-base.service';

const IDENTIFIER = 'exercise-chat/';

@Injectable({ providedIn: 'root' })
export class IrisExerciseChatService extends IrisChatService {
    exerciseId?: number;

    /**
     * Creates an instance of IrisExerciseChatService.
     * @param http The IrisChatHttpService for HTTP operations related to sessions.
     * @param ws The IrisChatWebsocketService for websocket operations
     * @param status The IrisStatusService for handling the status of the service.
     * @param userService The UserService for getting the current user.
     */
    constructor(
        public http: IrisChatHttpService,
        public ws: IrisWebsocketService,
        public status: IrisStatusService,
        userService: UserService,
    ) {
        super(http, ws, status, userService);
    }

    public changeToExercise(exerciseId?: number) {
        if (this.exerciseId === exerciseId) {
            return;
        }
        this.exerciseId = exerciseId;

        this.closeAndStart();
    }

    private closeAndStart() {
        this.close();

        if (this.exerciseId) {
            this.start();
        }
    }

    protected initAfterAccept() {
        this.closeAndStart();
    }

    protected getSessionCreationIdentifier(): string {
        return IDENTIFIER + this.exerciseId;
    }
}

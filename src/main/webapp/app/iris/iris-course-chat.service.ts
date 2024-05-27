import { Injectable } from '@angular/core';
import { IrisChatHttpService } from 'app/iris/iris-chat-http.service';
import { IrisWebsocketService } from 'app/iris/iris-websocket.service';
import { IrisStatusService } from 'app/iris/iris-status.service';
import { UserService } from 'app/core/user/user.service';
import { IrisChatService } from 'app/iris/iris-chat-base.service';
import { AccountService } from 'app/core/auth/account.service';

const IDENTIFIER = 'course-chat/';

@Injectable({ providedIn: 'root' })
export class IrisCourseChatService extends IrisChatService {
    courseId?: number;

    /**
     * Creates an instance of IrisCourseChatService.
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
        accountService: AccountService,
    ) {
        super(http, ws, status, userService, accountService);
    }

    public changeToCourse(courseId?: number) {
        if (this.courseId === courseId) {
            return;
        }
        this.courseId = courseId;

        this.closeAndStart();
    }

    private closeAndStart() {
        this.close();

        if (this.courseId) {
            this.start();
        }
    }

    protected initAfterAccept() {
        this.closeAndStart();
    }

    protected getSessionCreationIdentifier(): string {
        return IDENTIFIER + this.courseId;
    }
}

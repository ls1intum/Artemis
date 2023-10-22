import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisMessage, IrisUserMessage } from 'app/entities/iris/iris-message.model';
import { Component, Inject } from '@angular/core';
import { UserService } from 'app/core/user/user.service';
import { SharedService } from 'app/iris/shared.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { DOCUMENT } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { IrisChatSessionService } from 'app/iris/chat-session.service';

@Component({
    selector: 'jhi-tutor-chatbot-widget',
    templateUrl: './tutor-chatbot-widget.component.html',
    styleUrls: ['./tutor-chatbot-widget.component.scss'],
    animations: [
        trigger('fadeAnimation', [
            state(
                'start',
                style({
                    opacity: 1,
                }),
            ),
            state(
                'end',
                style({
                    opacity: 0,
                }),
            ),
            transition('start => end', [animate('2s ease')]),
        ]),
    ],
})
export class IrisTutorChatbotWidgetComponent extends IrisChatbotWidgetComponent {
    private chatSessionService: IrisChatSessionService;

    constructor(
        dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) data: any,
        userService: UserService,
        router: Router,
        sharedService: SharedService,
        modalService: NgbModal,
        @Inject(DOCUMENT) document: Document,
        translateService: TranslateService,
    ) {
        super(dialog, data, userService, router, sharedService, modalService, document, translateService);
        this.chatSessionService = data.sessionService as IrisChatSessionService;
    }

    protected getFirstMessageContent(): string {
        return this.translateService.instant('artemisApp.exerciseChatbot.tutorFirstMessage');
    }

    protected sendCreateRequest(message: IrisUserMessage): Promise<IrisMessage> {
        return this.chatSessionService.createMessage(this.sessionId, message);
    }

    protected sendResendRequest(message: IrisUserMessage): Promise<IrisMessage> {
        return this.chatSessionService.resendMessage(this.sessionId, message);
    }
}

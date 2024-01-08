import { NavigationStart, Router } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Component, Inject, OnDestroy } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog } from '@angular/material/dialog';
import { IrisStateStore } from 'app/iris/state-store.service';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { UserService } from 'app/core/user/user.service';
import { TranslateService } from '@ngx-translate/core';
import { IrisChatbotWidgetBasicComponent } from 'app/iris/exercise-chatbot/exercise-creation-ui/chatbot-widget-basic.component';

@Component({
    selector: 'jhi-chatbot-widget',
    templateUrl: './chatbot-widget.component.html',
    styleUrls: ['./chatbot-widget.component.scss'],
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
export class IrisChatbotWidgetComponent extends IrisChatbotWidgetBasicComponent implements OnDestroy {
    private navigationSubscription: Subscription;
    // data
    exerciseId?: number | undefined;
    courseId: number;
    stateStore: IrisStateStore;
    sessionService: IrisSessionService;
    constructor(
        dialog: MatDialog,
        @Inject(MAT_DIALOG_DATA) public data: any,
        userService: UserService,
        private router: Router,
        sharedService: SharedService,
        modalService: NgbModal,
        translateService: TranslateService,
    ) {
        super(dialog, userService, sharedService, modalService, translateService, document);
        this.stateStore = data.stateStore;
        this.courseId = data.courseId;
        this.exerciseId = Number.isNaN(data.exerciseId) ? undefined : data.exerciseId;
        this.sessionService = data.sessionService;
        this.navigationSubscription = this.router.events.subscribe((event) => {
            if (event instanceof NavigationStart) {
                this.dialog.closeAll();
            }
        });
        this.fullSize = data.fullSize ?? false;
        this.paramsOnSend = data.paramsOnSend;
    }

    ngOnDestroy() {
        super.ngOnDestroy();
        this.navigationSubscription.unsubscribe();
        this.toggleScrollLock(false);
    }

    /**
     * Closes the chat widget.
     */
    // closeChat() {
    //     super.closeChat();
    //     this.dialog.closeAll();
    // }
}

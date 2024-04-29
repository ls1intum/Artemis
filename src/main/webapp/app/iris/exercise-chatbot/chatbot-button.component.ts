import { faChevronDown, faCircle, faCommentDots } from '@fortawesome/free-solid-svg-icons';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { Overlay } from '@angular/cdk/overlay';
import { IrisStateStore } from 'app/iris/state-store.service';
import { NumNewMessagesResetAction } from 'app/iris/state-store.model';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { SharedService } from 'app/iris/shared.service';
import { IrisSessionService } from 'app/iris/session.service';

@Component({ template: '' })
export abstract class IrisChatbotButtonComponent implements OnInit, OnDestroy {
    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | null = null;
    chatOpen = false;
    hasNewMessages = false;
    protected courseId: number;
    protected exerciseId: number;
    private stateSubscription: Subscription;
    private chatOpenSubscription: Subscription;
    private paramsSubscription: Subscription;

    // Icons
    faCircle = faCircle;
    faCommentDots = faCommentDots;
    faChevronDown = faChevronDown;

    protected constructor(
        public dialog: MatDialog,
        protected overlay: Overlay,
        protected readonly sessionService: IrisSessionService,
        protected readonly stateStore: IrisStateStore,
        private route: ActivatedRoute,
        private sharedService: SharedService,
    ) {}

    ngOnInit() {
        // Subscribes to route params and gets the exerciseId from the route
        this.paramsSubscription = this.route.params.subscribe((params) => {
            if (this.exerciseId !== undefined && this.exerciseId !== parseInt(params['exerciseId'], 10)) {
                return;
            }
            this.courseId = parseInt(params['courseId'], 10);
            this.exerciseId = parseInt(params['exerciseId'], 10);
            if (this.exerciseId != null) {
                this.sessionService.getCurrentSessionOrCreate(this.exerciseId);
            }
        });

        // Subscribes to the stateStore to check for new messages
        this.stateSubscription = this.stateStore.getState().subscribe((state) => {
            this.hasNewMessages = state.numNewMessages > 0;
        });

        // Subscribes to the sharedService to get the status of chatOpen
        this.chatOpenSubscription = this.sharedService.chatOpen.subscribe((open) => (this.chatOpen = open));
    }

    ngOnDestroy() {
        // Closes the dialog if it is open
        if (this.dialogRef) {
            this.dialogRef.close();
        }
        // Unsubscribes from the stateSubscription
        this.stateSubscription?.unsubscribe();
        this.chatOpenSubscription?.unsubscribe();
        this.paramsSubscription?.unsubscribe();
    }

    /**
     * Handles the click event of the button.
     * If the chat is open, it resets the number of new messages, closes the dialog, and sets chatOpen to false.
     * If the chat is closed, it opens the chat dialog and sets chatOpen to true.
     */
    public handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
            this.stateStore.dispatch(new NumNewMessagesResetAction());
            this.dialog.closeAll();
            this.chatOpen = false;
        } else {
            this.openChat();
            this.chatOpen = true;
        }
    }

    /**
     * Opens the chat dialog using MatDialog.
     * Sets the configuration options for the dialog, including position, size, and data.
     */
    openChat() {
        this.chatOpen = true;
        this.dialogRef = this.dialog.open(IrisChatbotWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
            data: {
                stateStore: this.stateStore,
                courseId: this.courseId,
                exerciseId: this.exerciseId,
                sessionService: this.sessionService,
            },
        });
    }
}

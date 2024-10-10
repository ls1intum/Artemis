import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { Subscription } from 'rxjs';
import { faChevronDown, faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
})
export class IrisExerciseChatbotButtonComponent implements OnInit, OnDestroy {
    @Input()
    mode: ChatServiceMode;

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | null = null;
    chatOpen = false;
    hasNewMessages = false;
    private numNewMessagesSubscription: Subscription;
    private paramsSubscription: Subscription;

    // Icons
    faCircle = faCircle;
    faChevronDown = faChevronDown;

    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;
    protected readonly IrisLogoSize = IrisLogoSize;

    constructor(
        public dialog: MatDialog,
        protected overlay: Overlay,
        protected readonly chatService: IrisChatService,
        private route: ActivatedRoute,
    ) {}

    ngOnInit() {
        // Subscribes to route params and gets the exerciseId from the route
        this.paramsSubscription = this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            this.chatService.switchTo(this.mode, exerciseId);
        });

        // Subscribes to check for new messages
        this.numNewMessagesSubscription = this.chatService.numNewMessages.subscribe((num) => {
            this.hasNewMessages = num > 0;
        });
    }

    ngOnDestroy() {
        // Closes the dialog if it is open
        if (this.dialogRef) {
            this.dialogRef.close();
        }
        this.numNewMessagesSubscription?.unsubscribe();
        this.paramsSubscription.unsubscribe();
    }

    /**
     * Handles the click event of the button.
     * If the chat is open, it resets the number of new messages, closes the dialog, and sets chatOpen to false.
     * If the chat is closed, it opens the chat dialog and sets chatOpen to true.
     */
    public handleButtonClick() {
        if (this.chatOpen && this.dialogRef) {
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
        });
        this.dialogRef.afterClosed().subscribe(() => (this.chatOpen = false));
    }
}

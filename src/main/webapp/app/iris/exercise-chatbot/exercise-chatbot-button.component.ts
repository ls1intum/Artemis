import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, Subscription, filter, of, switchMap } from 'rxjs';
import { faAngleDoubleDown, faChevronDown, faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/iris-chat.service';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    animations: [
        trigger('expandAnimation', [
            state(
                'hidden',
                style({
                    opacity: 0,
                    transform: 'scale(0)',
                    transformOrigin: 'bottom right',
                }),
            ),
            state(
                'visible',
                style({
                    opacity: 1,
                    transform: 'scale(1)',
                    transformOrigin: 'bottom right',
                }),
            ),
            transition('hidden => visible', animate('300ms ease-out')),
            transition('visible => hidden', animate('300ms ease-in')),
        ]),
    ],
})
export class IrisExerciseChatbotButtonComponent implements OnInit, OnDestroy {
    @Input()
    mode: ChatServiceMode;

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | null = null;
    chatOpen = false;
    isOverflowing = false;
    hasNewMessages = false;
    newIrisMessage: string | undefined;

    private readonly CHAT_BUBBLE_TIMEOUT = 10000;

    private numNewMessagesSubscription: Subscription;
    private paramsSubscription: Subscription;
    private latestIrisMessageSubscription: Subscription;

    // Icons
    faCircle = faCircle;
    faChevronDown = faChevronDown;
    faAngleDoubleDown = faAngleDoubleDown;

    @ViewChild('chatBubble') chatBubble: ElementRef;

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
        this.latestIrisMessageSubscription = this.chatService.newIrisMessage
            .pipe(
                filter((msg) => !!msg),
                switchMap((msg) => {
                    if (msg!.content && msg!.content.length > 0) {
                        return of((msg!.content[0] as IrisTextMessageContent).textContent);
                    }
                    return EMPTY;
                }),
            )
            .subscribe((message) => {
                this.newIrisMessage = message;
                setTimeout(() => this.checkOverflow(), 0);
                setTimeout(() => {
                    this.newIrisMessage = undefined;
                    this.isOverflowing = false;
                }, this.CHAT_BUBBLE_TIMEOUT);
            });
    }

    ngOnDestroy() {
        // Closes the dialog if it is open
        if (this.dialogRef) {
            this.dialogRef.close();
        }
        this.numNewMessagesSubscription?.unsubscribe();
        this.paramsSubscription.unsubscribe();
        this.latestIrisMessageSubscription.unsubscribe();
        this.newIrisMessage = undefined;
        this.isOverflowing = false;
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
     * Checks if the chat bubble is overflowing and sets isOverflowing to true if it is.
     */
    public checkOverflow() {
        const element = this.chatBubble?.nativeElement;
        this.isOverflowing = !!element && element.scrollHeight > element.clientHeight;
    }

    /**
     * Opens the chat dialog using MatDialog.
     * Sets the configuration options for the dialog, including position, size, and data.
     */
    public openChat() {
        this.chatOpen = true;
        this.newIrisMessage = undefined;
        this.isOverflowing = false;
        this.dialogRef = this.dialog.open(IrisChatbotWidgetComponent, {
            hasBackdrop: false,
            scrollStrategy: this.overlay.scrollStrategies.noop(),
            position: { bottom: '0px', right: '0px' },
            disableClose: true,
        });
        this.dialogRef.afterClosed().subscribe(() => this.handleDialogClose());
    }

    private handleDialogClose() {
        this.chatOpen = false;
        this.newIrisMessage = undefined;
    }

    protected readonly IrisTextMessageContent = IrisTextMessageContent;
}

import { Component, ElementRef, Input, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { Overlay } from '@angular/cdk/overlay';
import { ActivatedRoute } from '@angular/router';
import { IrisChatbotWidgetComponent } from 'app/iris/overview/exercise-chatbot/widget/chatbot-widget.component';
import { EMPTY, Subscription, filter, of, switchMap } from 'rxjs';
import { faAngleDoubleDown, faChevronDown, faCircle } from '@fortawesome/free-solid-svg-icons';
import { IrisLogoLookDirection, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/overview/iris-logo/iris-logo.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';

@Component({
    selector: 'jhi-exercise-chatbot-button',
    templateUrl: './exercise-chatbot-button.component.html',
    styleUrls: ['./exercise-chatbot-button.component.scss'],
    imports: [NgClass, TranslateDirective, FaIconComponent, IrisLogoComponent, HtmlForMarkdownPipe],
})
export class IrisExerciseChatbotButtonComponent implements OnInit, OnDestroy {
    protected readonly faCircle = faCircle;
    protected readonly faChevronDown = faChevronDown;
    protected readonly faAngleDoubleDown = faAngleDoubleDown;

    private readonly dialog = inject(MatDialog);
    private readonly overlay = inject(Overlay);
    private readonly chatService = inject(IrisChatService);
    private readonly route = inject(ActivatedRoute);

    private readonly CHAT_BUBBLE_TIMEOUT = 10000;

    protected readonly IrisLogoLookDirection = IrisLogoLookDirection;
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;

    @Input() mode: ChatServiceMode;

    dialogRef: MatDialogRef<IrisChatbotWidgetComponent> | undefined = undefined;
    chatOpen = false;
    isOverflowing = false;
    hasNewMessages = false;
    newIrisMessage: string | undefined;

    private numNewMessagesSubscription: Subscription;
    private paramsSubscription: Subscription;
    private latestIrisMessageSubscription: Subscription;
    private queryParamsSubscription: Subscription;

    @ViewChild('chatBubble') chatBubble: ElementRef;

    ngOnInit() {
        // Subscribes to route params and gets the exerciseId from the route
        this.paramsSubscription = this.route.params.subscribe((params) => {
            const rawId = this.mode == ChatServiceMode.LECTURE ? params['lectureId'] : params['exerciseId'];
            const id = parseInt(rawId, 10);
            this.chatService.switchTo(this.mode, id);
        });

        this.queryParamsSubscription = this.route.queryParams?.subscribe((params: any) => {
            if (params.irisQuestion) {
                this.openChat();
            }
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
        this.queryParamsSubscription?.unsubscribe();
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
}

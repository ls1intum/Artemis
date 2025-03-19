import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/iris-chat.service';
import { Post } from 'app/entities/metis/post.model';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription } from 'rxjs';
import { IrisMessage } from 'app/entities/iris/iris-message.model';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/entities/iris/iris-content-type.model';

/**
 * Component to display the tutor suggestion in the course overview
 * The tutor suggestion is displayed in a thread and is used to suggest answers for a tutor
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe],
})
export class TutorSuggestionComponent implements OnInit, OnChanges, OnDestroy {
    protected readonly chatService = inject(IrisChatService);

    messagesSubscription: Subscription;

    messages: IrisMessage[];
    suggestion: IrisMessage | undefined;

    post = input<Post>();

    ngOnInit(): void {
        const post = this.post();
        if (post) {
            this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
        }
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestion = messages.first();
            }
            this.suggestion = messages.first();
            this.messages = messages;
        });
    }

    ngOnChanges(): void {
        const post = this.post();
        if (post) {
            this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
        }
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestion = messages.first();
            }
            this.suggestion = messages.first();
            this.messages = messages;
        });
    }

    ngOnDestroy() {
        this.messagesSubscription.unsubscribe();
    }

    sendMessage(): void {
        this.chatService.sendMessage('test').subscribe((m) => {});
    }

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
}

import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/iris-chat.service';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { Course } from 'app/core/shared/entities/course.model';
import { Post } from 'app/communication/shared/entities/post.model';

/**
 * Component to display the tutor suggestion in the chat
 * It fetches the messages from the chat service and displays the suggestion
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe],
})
export class TutorSuggestionComponent implements OnInit, OnChanges, OnDestroy {
    protected readonly chatService = inject(IrisChatService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);

    messagesSubscription: Subscription;
    profileSubscription: Subscription;
    irisSettingsSubscription: Subscription;

    messages: IrisMessage[];
    suggestion: IrisMessage | undefined;

    irisEnabled = false;

    post = input<Post>();
    course = input<Course>();

    ngOnInit(): void {
        const post = this.post();
        const course = this.course();
        this.profileSubscription = this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo?.activeProfiles.includes(PROFILE_IRIS)) {
                if (course?.id && post) {
                    this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(course.id).subscribe((settings) => {
                        this.irisEnabled = !!settings?.irisTutorSuggestionSettings?.enabled;
                        if (this.irisEnabled) {
                            this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                            this.fetchMessages();
                        }
                    });
                }
            }
        });
    }

    ngOnChanges(): void {
        if (this.irisEnabled) {
            const post = this.post();
            if (post) {
                this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                this.messagesSubscription?.unsubscribe();

                // Subscribe to sessionId$ to ensure that the session is created before fetching messages
                this.chatService.sessionId$
                    .pipe(
                        filter((id): id is number => !!id), // Ensuring that id is not null or undefined
                        take(1),
                    )
                    .subscribe(() => {
                        this.requestSuggestion();
                    });
            }
            this.fetchMessages();
        }
    }

    ngOnDestroy() {
        this.messagesSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.irisSettingsSubscription?.unsubscribe();
    }

    /**
     * Requests a tutor suggestion from the chat service
     * This method is called when the component is initialized or when the post changes
     */
    requestSuggestion(): void {
        const post = this.post();
        if (post) {
            this.chatService.requestTutorSuggestion(post).subscribe();
        }
    }

    /**
     * Fetches the messages from the chat service and updates the suggestion if necessary
     */
    private fetchMessages(): void {
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestion = messages.findLast((m) => m.sender === IrisSender.LLM);
            }
            this.messages = messages;
        });
    }

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
}

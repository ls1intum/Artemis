import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription } from 'rxjs';
import { filter, take } from 'rxjs/operators';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { faArrowsRotate, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';

/**
 * Component to display the tutor suggestion in the chat
 * It fetches the messages from the chat service and displays the suggestion
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe, FaIconComponent, ChatStatusBarComponent],
})
export class TutorSuggestionComponent implements OnInit, OnChanges, OnDestroy {
    protected readonly chatService = inject(IrisChatService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);
    private accountService = inject(AccountService);

    messagesSubscription: Subscription;
    irisSettingsSubscription: Subscription;
    tutorSuggestionSubscription: Subscription;
    stagesSubscription: Subscription;
    errorSubscription: Subscription;

    messages: IrisMessage[];
    suggestion: IrisMessage | undefined;

    stages?: IrisStageDTO[] = [];
    error?: IrisErrorMessageKey;

    irisEnabled = false;

    post = input<Post>();
    course = input<Course>();

    faArrowsRotate = faArrowsRotate;
    faCircleXmark = faCircleXmark;

    ngOnInit(): void {
        const course = this.course();
        if (!this.accountService.isAtLeastTutorInCourse(course)) {
            return;
        }
        const post = this.post();
        if (!this.profileService.isProfileActive(PROFILE_IRIS)) {
            return;
        }
        if (course?.id && post) {
            this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(course.id).subscribe((settings) => {
                this.irisEnabled = !!settings?.irisTutorSuggestionSettings?.enabled;
                if (this.irisEnabled) {
                    this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                    this.chatService.sessionId$
                        .pipe(
                            filter((id): id is number => !!id), // Ensuring that id is not null or undefined
                            take(1),
                        )
                        .subscribe(() => {
                            this.requestSuggestion();
                        });
                    this.fetchMessages();
                }
            });
        }
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
        this.irisSettingsSubscription?.unsubscribe();
        this.tutorSuggestionSubscription?.unsubscribe();
        this.stagesSubscription?.unsubscribe();
        this.errorSubscription?.unsubscribe();
    }

    /**
     * Requests a tutor suggestion from the chat service
     * This method is called when the component is initialized or when the post changes
     */
    requestSuggestion(): void {
        const post = this.post();
        if (post) {
            this.tutorSuggestionSubscription = this.chatService.requestTutorSuggestion(post).subscribe();
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
        this.stagesSubscription = this.chatService.currentStages().subscribe((stages) => {
            this.stages = stages;
        });
        this.errorSubscription = this.chatService.currentError().subscribe((error) => (this.error = error));
    }

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;
}

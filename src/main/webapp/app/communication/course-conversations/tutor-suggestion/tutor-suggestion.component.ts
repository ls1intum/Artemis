import { Component, OnChanges, OnDestroy, OnInit, inject, input } from '@angular/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription, of } from 'rxjs';
import { catchError, distinctUntilChanged, filter, shareReplay, skip, switchMap, take, tap } from 'rxjs/operators';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { PROFILE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { ChatStatusBarComponent } from 'app/iris/overview/base-chatbot/chat-status-bar/chat-status-bar.component';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';

/**
 * Component to display the tutor suggestion in the chat
 * It fetches the messages from the chat service and displays the suggestion
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe, ChatStatusBarComponent, TranslateDirective],
})
export class TutorSuggestionComponent implements OnInit, OnChanges, OnDestroy {
    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;

    protected readonly chatService = inject(IrisChatService);
    private profileService = inject(ProfileService);
    private irisSettingsService = inject(IrisSettingsService);
    private accountService = inject(AccountService);
    private statusService = inject(IrisStatusService);
    private featureToggleService = inject(FeatureToggleService);

    irisActive$ = this.statusService.getActiveStatus().pipe(shareReplay(1));

    irisIsActive = false;

    messagesSubscription: Subscription;
    irisSettingsSubscription: Subscription;
    tutorSuggestionSubscription: Subscription;
    stagesSubscription: Subscription;
    errorSubscription: Subscription;
    irisActivationSubscription: Subscription;
    featureToggleSubscription: Subscription;

    messages: IrisMessage[];
    suggestion: IrisMessage | undefined;

    stages?: IrisStageDTO[] = [];
    error?: IrisErrorMessageKey;

    irisEnabled = false;
    isAtLeastTutor = false;

    post = input<Post>();
    course = input<Course>();

    ngOnInit(): void {
        this.featureToggleSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.TutorSuggestions).subscribe((active) => {
            if (active) {
                this.subscribeToIrisActivation();

                if (!this.profileService.isProfileActive(PROFILE_IRIS)) {
                    return;
                }
                const course = this.course();
                const post = this.post();
                this.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(course);
                if (!this.isAtLeastTutor || post?.resolved) {
                    return;
                }
                if (course?.id && post) {
                    this.irisSettingsSubscription = this.irisSettingsService.getCombinedCourseSettings(course.id).subscribe((settings) => {
                        this.irisEnabled = !!settings?.irisTutorSuggestionSettings?.enabled;
                        if (this.irisEnabled) {
                            this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                            this.fetchMessages();
                        }
                    });
                }
            } else {
                this.irisEnabled = false;
            }
        });
    }

    ngOnChanges(): void {
        if (this.irisEnabled) {
            const post = this.post();
            if (post) {
                this.chatService.switchTo(ChatServiceMode.TUTOR_SUGGESTION, post.id);
                this.messagesSubscription?.unsubscribe();

                this.subscribeToIrisActivation();
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
        this.irisActivationSubscription?.unsubscribe();
        this.featureToggleSubscription?.unsubscribe();
    }

    /**
     * Requests a tutor suggestion from the chat service
     * This method is called when the component is initialized or when the post changes
     */
    requestSuggestion(): void {
        const post = this.post();
        if (!post) {
            return;
        }

        const waitForSessionAndMessages$ = this.chatService.currentSessionId().pipe(
            filter((id): id is number => !!id),
            take(1),
            switchMap(() =>
                this.chatService.currentMessages().pipe(
                    skip(1), // The initial message is not relevant as the system is sending an empty array first
                    take(1),
                    catchError((err) => {
                        this.error = IrisErrorMessageKey.SESSION_LOAD_FAILED;
                        return of([]);
                    }),
                ),
            ),
        );

        this.tutorSuggestionSubscription = waitForSessionAndMessages$.subscribe((messages) => {
            const lastMessage = messages[messages.length - 1];
            const shouldRequest = messages.length === 0 || !(lastMessage?.sender === IrisSender.LLM || lastMessage?.sender === IrisSender.ARTIFACT);
            if (shouldRequest) {
                this.chatService
                    .requestTutorSuggestion()
                    .pipe(
                        catchError((err) => {
                            this.error = IrisErrorMessageKey.SEND_MESSAGE_FAILED;
                            return of(undefined);
                        }),
                    )
                    .subscribe();
            }
        });
    }

    /**
     * Fetches the messages from the chat service and updates the suggestion if necessary
     */
    private fetchMessages(): void {
        this.messagesSubscription = this.chatService.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestion = messages.findLast((m) => m.sender === IrisSender.ARTIFACT);
            }
            this.messages = messages;
        });
        this.stagesSubscription = this.chatService.currentStages().subscribe((stages) => {
            this.stages = stages;
        });
        this.errorSubscription = this.chatService.currentError().subscribe((error) => (this.error = error));
    }

    private subscribeToIrisActivation(): void {
        this.irisActivationSubscription?.unsubscribe();
        this.irisActivationSubscription = this.irisActive$
            .pipe(
                tap((active) => (this.irisIsActive = active)),
                distinctUntilChanged(),
                switchMap((active) => {
                    if (!active) {
                        return of(undefined);
                    }
                    return this.chatService.currentSessionId().pipe(
                        filter((id): id is number => !!id),
                        take(1),
                    );
                }),
                filter((id): id is number => !!id),
            )
            .subscribe(() => this.requestSuggestion());
    }
}

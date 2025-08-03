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
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { faArrowDown, faArrowUp } from '@fortawesome/free-solid-svg-icons';

/**
 * Component to display the tutor suggestion in the chat
 * It fetches the messages from the chat service and displays the suggestion
 */
@Component({
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrl: './tutor-suggestion.component.scss',
    imports: [IrisLogoComponent, AsPipe, FormsModule, TranslateDirective, IrisBaseChatbotComponent, ButtonComponent],
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
    suggestions: IrisMessage[] = [];

    faArrowUp = faArrowUp;
    upDisabled = true;
    faArrowDown = faArrowDown;
    downDisabled = true;

    stages?: IrisStageDTO[] = [];
    error?: IrisErrorMessageKey;

    irisEnabled = false;
    isAtLeastTutor = false;

    post = input<Post>();
    course = input<Course>();

    ngOnInit(): void {
        this.featureToggleSubscription = this.featureToggleService.getFeatureToggleActive(FeatureToggle.TutorSuggestions).subscribe((active) => {
            if (active) {
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
                            this.subscribeToIrisActivation();
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
                this.suggestions = messages.filter((message) => message.sender === IrisSender.ARTIFACT);
                this.suggestion = this.suggestions.last();
                if (this.suggestions.length > 0) {
                    this.updateArrowDisabled(this.suggestions.length - 1);
                }
            }
            this.messages = messages;
        });
        this.stagesSubscription = this.chatService.currentStages().subscribe((stages) => {
            this.stages = stages;
        });
        this.errorSubscription = this.chatService.currentError().subscribe((error) => (this.error = error));
    }

    /**
     * Subscribes to the Iris activation status and requests a suggestion when Iris is activated
     * This method ensures that the suggestion is requested only when Iris is active and the session ID is available
     */
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

    /**
     * Returns a timestamp string based on the provided date.
     * If the date is today, it returns "just now", "X minutes ago", or "X hours ago".
     * If the date is yesterday, it returns "yesterday".
     * If the date is within the last 7 days, it returns "X days ago".
     * Otherwise, it returns the date formatted as "DD/MM/YYYY".
     * @param date - The date to format, can be a dayjs object, string, or Date object.
     * @returns A formatted timestamp string or an empty string if the date is undefined.
     */
    getTimestamp(date: dayjs.Dayjs | string | Date | undefined): string | undefined {
        if (!date) {
            return '';
        }

        const parsedDate = dayjs(date);
        const now = dayjs();

        if (parsedDate.isSame(now, 'day')) {
            const diffMinutes = now.diff(parsedDate, 'minute');
            const diffHours = now.diff(parsedDate, 'hour');
            if (diffMinutes < 1) {
                return 'just now';
            } else if (diffMinutes < 60) {
                return `${diffMinutes} minutes ago`;
            } else {
                return `${diffHours} hours ago`;
            }
        } else {
            const diffDays = now.diff(parsedDate, 'day');
            if (diffDays < 1) {
                return 'yesterday';
            } else if (diffDays < 7) {
                return `${diffDays} days ago`;
            } else {
                return parsedDate.format('DD/MM/YYYY');
            }
        }
    }

    /**
     * Switches between suggestions based on the provided direction.
     * If `up` is true, it switches to the next suggestion; if false,
     * it switches to the previous suggestion.
     * @param up
     */
    switchSuggestion(up: boolean) {
        if (!this.suggestion || !this.suggestions) {
            return;
        }

        const currentIndex = this.suggestions.findIndex((message) => message.id === this.suggestion?.id);
        if (currentIndex === -1) {
            return;
        }

        const newIndex = up ? currentIndex + 1 : currentIndex - 1;

        if (newIndex < 0 || newIndex >= this.suggestions.length) {
            this.updateArrowDisabled(currentIndex);
            return;
        }

        this.suggestion = this.suggestions[newIndex];
        this.updateArrowDisabled(newIndex);
    }

    private updateArrowDisabled(currentIndex: number) {
        this.downDisabled = currentIndex === 0;
        this.upDisabled = currentIndex === this.suggestions.length - 1;
    }
}

import { Component, OnDestroy, OnInit, effect, inject, input, untracked } from '@angular/core';
import { IrisLogoComponent, IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { Subscription, of } from 'rxjs';
import { catchError, distinctUntilChanged, filter, map, pairwise, shareReplay, switchMap, take, tap } from 'rxjs/operators';
import { AsPipe } from 'app/shared/pipes/as.pipe';
import { IrisTextMessageContent } from 'app/iris/shared/entities/iris-content-type.model';
import { MODULE_FEATURE_IRIS } from 'app/app.constants';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IrisMessage, IrisSender } from 'app/iris/shared/entities/iris-message.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { IrisStageDTO } from 'app/iris/shared/entities/iris-stage-dto.model';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ChatServiceMode } from 'app/iris/shared/entities/iris-chat-mode.model';
import { IrisChatControllerService } from 'app/iris/overview/services/iris-chat-controller.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { FormsModule } from '@angular/forms';
import dayjs from 'dayjs/esm';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { faArrowDown, faArrowUp, faArrowsRotate } from '@fortawesome/free-solid-svg-icons';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

/**
 * Component to display the tutor suggestion in the chat
 * It fetches the messages from the chat service and displays the suggestion
 */
@Component({
    standalone: true,
    selector: 'jhi-tutor-suggestion',
    templateUrl: './tutor-suggestion.component.html',
    styleUrls: ['./tutor-suggestion.component.scss'],
    imports: [IrisLogoComponent, AsPipe, FormsModule, TranslateDirective, IrisBaseChatbotComponent, ButtonComponent, ArtemisDatePipe, ArtemisTimeAgoPipe, NgbTooltip],
    providers: [IrisChatControllerService],
})
export class TutorSuggestionComponent implements OnInit, OnDestroy {
    private initialized = false;

    constructor() {
        effect(() => {
            const post = this.post();
            const course = this.course();
            untracked(() => {
                if (this.initialized && this.irisEnabled) {
                    if (post && course?.id) {
                        this.controller.setContext(course.id, ChatServiceMode.TUTOR_SUGGESTION, post.id);
                        this.messagesSubscription?.unsubscribe();
                        this.subscribeToIrisActivation();
                    }
                    this.fetchMessages();
                }
            });
        });
    }

    protected readonly IrisLogoSize = IrisLogoSize;
    protected readonly IrisTextMessageContent = IrisTextMessageContent;

    protected readonly faArrowUp = faArrowUp;
    protected readonly faArrowsRotate = faArrowsRotate;
    protected readonly faArrowDown = faArrowDown;

    protected readonly controller = inject(IrisChatControllerService);
    private readonly profileService = inject(ProfileService);
    private readonly irisSettingsService = inject(IrisSettingsService);
    private readonly accountService = inject(AccountService);
    private readonly featureToggleService = inject(FeatureToggleService);

    irisActive$ = this.controller.getActiveStatus().pipe(shareReplay(1));

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

    upDisabled = true;
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
                if (!this.profileService.isModuleFeatureActive(MODULE_FEATURE_IRIS)) {
                    return;
                }
                const course = this.course();
                const post = this.post();
                this.isAtLeastTutor = this.accountService.isAtLeastTutorInCourse(course);
                if (!this.isAtLeastTutor || post?.resolved) {
                    return;
                }
                if (course?.id && post) {
                    this.irisSettingsSubscription = this.irisSettingsService.getCourseSettingsWithRateLimit(course.id).subscribe((response) => {
                        this.irisEnabled = !!response?.settings?.enabled;
                        if (this.irisEnabled) {
                            this.controller.setContext(course.id, ChatServiceMode.TUTOR_SUGGESTION, post.id);
                            this.subscribeToIrisActivation();
                            this.fetchMessages();
                        }
                    });
                }
            } else {
                this.irisEnabled = false;
            }
        });
        this.initialized = true;
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

        // Wait for the session to be ready, then take the second `currentMessages` emission.
        // The first is the initial empty array; pairwise+take(1) is equivalent to skip(1)+take(1)
        // but tolerates emissions of any shape (including empty arrays) without filtering them out.
        const waitForSessionAndMessages$ = this.controller.currentSessionId().pipe(
            filter((id): id is number => !!id),
            take(1),
            switchMap(() =>
                this.controller.currentMessages().pipe(
                    pairwise(),
                    take(1),
                    map(([, curr]) => curr),
                    catchError(() => {
                        this.error = IrisErrorMessageKey.SESSION_LOAD_FAILED;
                        return of([]);
                    }),
                ),
            ),
        );

        // Cancel any prior in-flight wait chain before starting a new one. Repeat triggers
        // (post change, iris re-activation) would otherwise leak older subscriptions and let
        // duplicate or outdated suggestion requests fire against stale sessions/posts.
        this.tutorSuggestionSubscription?.unsubscribe();
        this.tutorSuggestionSubscription = waitForSessionAndMessages$.subscribe((messages) => {
            const lastMessage = messages[messages.length - 1];
            const lastThreadMessageAfterLastSuggestion = this.checkForNewAnswerAndRequestSuggestion();
            const shouldRequest =
                lastThreadMessageAfterLastSuggestion || messages.length === 0 || !(lastMessage?.sender === IrisSender.LLM || lastMessage?.sender === IrisSender.ARTIFACT);
            if (shouldRequest) {
                this.controller
                    .requestTutorSuggestion()
                    .pipe(
                        catchError(() => {
                            this.error = IrisErrorMessageKey.SEND_MESSAGE_FAILED;
                            return of(undefined);
                        }),
                    )
                    .subscribe();
            }
        });
    }

    /**
     * Handles the user request for a new suggestion
     * This method is called when the user clicks the "New Suggestion" button
     */
    userRequestedNewSuggestion(): void {
        this.controller
            .requestTutorSuggestion()
            .pipe(
                catchError(() => {
                    this.error = IrisErrorMessageKey.SEND_MESSAGE_FAILED;
                    return of(undefined);
                }),
            )
            .subscribe();
    }

    /**
     * Fetches the messages from the chat service and updates the suggestion if necessary
     */
    private fetchMessages(): void {
        this.messagesSubscription?.unsubscribe();
        this.stagesSubscription?.unsubscribe();
        this.errorSubscription?.unsubscribe();
        this.messagesSubscription = this.controller.currentMessages().subscribe((messages) => {
            if (messages.length !== this.messages?.length) {
                this.suggestions = messages.filter((message) => message.sender === IrisSender.ARTIFACT);
                this.suggestion = this.suggestions.last();
                if (this.suggestions.length > 0) {
                    this.updateArrowDisabled(this.suggestions.length - 1);
                }
            }
            this.messages = messages;
        });
        this.stagesSubscription = this.controller.currentStages().subscribe((stages) => {
            this.stages = stages;
        });
        this.errorSubscription = this.controller.currentError().subscribe((error) => (this.error = error));
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
                    return this.controller.currentSessionId().pipe(
                        filter((id): id is number => !!id),
                        take(1),
                    );
                }),
                filter((id): id is number => !!id),
            )
            .subscribe(() => this.requestSuggestion());
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

    /**
     * Checks if a new answer was added to the post after the last suggestion,
     * and requests a new suggestion if needed.
     */
    private checkForNewAnswerAndRequestSuggestion(): boolean {
        const post = this.post();
        if (!post || !post.answers || post.answers.length === 0 || this.suggestions.length === 0) {
            return false;
        }

        const latestAnswer = post.answers.reduce((latest, current) => {
            return dayjs(current.creationDate).isAfter(dayjs(latest.creationDate)) ? current : latest;
        });

        const lastSuggestion = this.suggestions[this.suggestions.length - 1];
        if (!lastSuggestion || !lastSuggestion.sentAt) {
            return false;
        }

        return !!dayjs(latestAnswer.creationDate).isAfter(dayjs(lastSuggestion.sentAt));
    }
}

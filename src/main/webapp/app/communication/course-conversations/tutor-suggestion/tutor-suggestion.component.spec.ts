import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, ComponentRef, input, output } from '@angular/core';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { concat, of, throwError } from 'rxjs';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { mockCourseSettings } from 'test/helpers/mocks/iris/mock-settings';
import { AccountService } from 'app/core/auth/account.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateService } from '@ngx-translate/core';
import { IrisErrorMessageKey } from 'app/iris/shared/entities/iris-errors.model';
import { ActivatedRoute } from '@angular/router';
import { UserService } from 'app/core/user/shared/user.service';
import dayjs from 'dayjs/esm';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/core/user/user.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { IrisBaseChatbotComponent } from 'app/iris/overview/base-chatbot/iris-base-chatbot.component';

// Simple mock to avoid ng-mocks issues with signal-based viewChild
@Component({
    selector: 'jhi-iris-base-chatbot',
    template: '',
    standalone: true,
})
class MockIrisBaseChatbotComponent {
    readonly showDeclineButton = input<boolean>();
    readonly isChatHistoryAvailable = input<boolean>();
    readonly isEmbeddedChat = input<boolean>();
    readonly fullSize = input<boolean>();
    readonly showCloseButton = input<boolean>();
    readonly isChatGptWrapper = input<boolean>();
    readonly fullSizeToggle = output<void>();
    readonly closeClicked = output<void>();
}

describe('TutorSuggestionComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorSuggestionComponent;
    let componentRef: ComponentRef<TutorSuggestionComponent>;
    let fixture: ComponentFixture<TutorSuggestionComponent>;
    let courseSettingsService: IrisSettingsService;
    let profileService: ProfileService;
    let chatService: IrisChatService;
    let accountService: AccountService;
    let featureToggleService: FeatureToggleService;
    let irisStatusService: IrisStatusService;
    const courseSettings = mockCourseSettings(1, true);

    const statusMock = {
        currentRatelimitInfo: vi.fn().mockReturnValue(of({})),
        handleRateLimitInfo: vi.fn(),
        getActiveStatus: vi.fn().mockReturnValue(of({})),
        setCurrentCourse: vi.fn(),
    } as any;
    const mockUserService = {
        updateLLMSelectionDecision: vi.fn(),
    } as any;

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorSuggestionComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: UserService, useValue: mockUserService },
                { provide: IrisStatusService, useValue: statusMock },
                MockProvider(IrisSettingsService),
                MockProvider(ProfileService),
                MockProvider(FeatureToggleService),
                MockProvider(ActivatedRoute),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).overrideComponent(TutorSuggestionComponent, {
            remove: { imports: [IrisBaseChatbotComponent, ArtemisTimeAgoPipe] },
            add: { imports: [MockIrisBaseChatbotComponent, MockPipe(ArtemisTimeAgoPipe)] },
        });

        fixture = TestBed.createComponent(TutorSuggestionComponent);
        chatService = TestBed.inject(IrisChatService);
        chatService.setCourseId(123);

        irisStatusService = TestBed.inject(IrisStatusService);
        vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

        component = fixture.componentInstance;
        componentRef = fixture.componentRef;

        courseSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        accountService = TestBed.inject(AccountService);
        featureToggleService = TestBed.inject(FeatureToggleService);

        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        chatService.setCourseId(123);
        accountService.userIdentity.set({ selectedLLMUsageTimestamp: dayjs() } as User);

        componentRef.setInput('post', { id: 1 } as any);
        componentRef.setInput('course', { id: 1 } as any);
    });

    it('should initialize and switch chat service if IRIS is enabled', () => {
        vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(courseSettings));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        const switchToSpy = vi.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        const getFeatureToggleSpy = vi.spyOn(featureToggleService, 'getFeatureToggleActive');
        const getActiveStatusSpy = vi.spyOn(irisStatusService, 'getActiveStatus');
        fixture.detectChanges();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
        expect(getFeatureToggleSpy).toHaveBeenCalledWith(FeatureToggle.TutorSuggestions);
        expect(getActiveStatusSpy).toHaveBeenCalled();
    });

    it('should initialize properly in ngOnInit and load settings', () => {
        vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        const getCourseSettingsSpy = vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(courseSettings));
        const profileServiceMock = vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        const switchToSpy = vi.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(profileServiceMock).toHaveBeenCalledOnce();
        expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    });

    describe('should set irisEnabled to', () => {
        it('false if Iris module feature is not enabled', () => {
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            vi.advanceTimersByTime(0);

            expect(component['irisEnabled']).toBe(false);
        });

        it('false if settings are not available', () => {
            vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(undefined));
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            vi.advanceTimersByTime(0);

            expect(component['irisEnabled']).toBe(false);
        });

        it('false if course id is not available', () => {
            componentRef.setInput('course', null);
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);

            fixture.detectChanges();
            vi.advanceTimersByTime(0);

            expect(component['irisEnabled']).toBe(false);
        });

        it('false if post id is not available', () => {
            componentRef.setInput('post', null);
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            vi.advanceTimersByTime(0);

            expect(component['irisEnabled']).toBe(false);
        });

        it('true if all conditions are met', () => {
            vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
            vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(courseSettings));
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
            fixture.detectChanges();
            vi.advanceTimersByTime(0);
            expect(component['irisEnabled']).toBe(true);
        });

        it('should set irisEnabled to false if feature toggle is disabled', () => {
            vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(false));
            fixture.detectChanges();
            vi.advanceTimersByTime(0);
            expect(component['irisEnabled']).toBe(false);
        });
    });

    it('should call requestSuggestion when post input changes', () => {
        const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        setUpForRequestSuggestion();
        // Trigger the effect by changing the post input
        componentRef.setInput('post', { id: 2 } as any);
        fixture.detectChanges();
        vi.advanceTimersByTime(0);
        expect(requestTutorSuggestionSpy).toHaveBeenCalled();
    });

    it('should not call requestSuggestion if iris is active but no sessionId', () => {
        vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(undefined));
        vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
        const requestSuggestionSpy = vi.spyOn(component as any, 'requestSuggestion');
        (component as any).subscribeToIrisActivation();
        vi.advanceTimersByTime(0);
        expect(requestSuggestionSpy).not.toHaveBeenCalled();
    });

    it('should unsubscribe from all services on destroy', () => {
        vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(courseSettings));
        vi.spyOn(chatService, 'currentStages').mockReturnValue(of([]));

        vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([])));
        vi.spyOn(chatService, 'currentError').mockReturnValue(of());
        vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
        component.ngOnInit();
        const irisUnsubSpy = vi.spyOn(component['irisSettingsSubscription'], 'unsubscribe');
        const msgUnsubSpy = vi.spyOn(component['messagesSubscription'], 'unsubscribe');
        const stagesUnsubSpy = vi.spyOn(component['stagesSubscription'], 'unsubscribe');
        const errorUnsubSpy = vi.spyOn(component['errorSubscription'], 'unsubscribe');
        const tutorSuggestionUnsubSpy = vi.spyOn(component['tutorSuggestionSubscription'], 'unsubscribe');

        component.ngOnDestroy();

        expect(irisUnsubSpy).toHaveBeenCalled();
        expect(msgUnsubSpy).toHaveBeenCalled();
        expect(stagesUnsubSpy).toHaveBeenCalled();
        expect(errorUnsubSpy).toHaveBeenCalled();
        expect(tutorSuggestionUnsubSpy).toHaveBeenCalled();
    });

    it('should update suggestion in fetchMessages if last message is an Artifact', () => {
        const mockMessages = [{ id: 1, sender: 'USER' } as IrisMessage, { id: 2, sender: 'ARTIFACT' } as IrisMessage];
        vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
        vi.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        vi.spyOn(chatService, 'currentError').mockReturnValue(of());
        component['fetchMessages']();
        vi.advanceTimersByTime(0);
        expect(component.suggestion).toEqual(mockMessages[1]);
        expect(component.messages).toEqual(mockMessages);
    });

    describe('requestSuggestion', () => {
        it('should request suggestion when there are no messages after error fallback', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(
                concat(
                    throwError(() => new Error('empty')),
                    of([]),
                ),
            );
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        });

        it('should request suggestion if messages array is empty', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        });

        it('should not request suggestion if post is undefined', () => {
            componentRef.setInput('post', undefined as any);
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });

        it('should request suggestion when second message emission contains LLM message', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        });

        it('should set error and still request suggestion if currentMessages fails', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(throwError(() => new Error('fail')));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(component['error']).toBe(IrisErrorMessageKey.SESSION_LOAD_FAILED);
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        });

        it('should not request suggestion when last message is not from LLM', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            const mockMessages = [{ id: 1, sender: 'USER' }] as IrisMessage[];
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(requestTutorSuggestionSpy).not.toHaveBeenCalled();
        });

        it('should not request suggestion if last message is from LLM and no new answer', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(
                of([
                    { id: 1, sender: 'USER' },
                    { id: 2, sender: 'LLM' },
                ] as IrisMessage[]),
            );
            vi.spyOn(component as any, 'checkForNewAnswerAndRequestSuggestion').mockReturnValue(false);
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });

        it('should not request suggestion if last message is from LLM or ARTIFACT', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            const mockMessages = [
                { id: 1, sender: 'USER' },
                { id: 2, sender: 'LLM' },
            ] as IrisMessage[];
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());

            component['requestSuggestion']();
            vi.advanceTimersByTime(0);

            expect(requestTutorSuggestionSpy).not.toHaveBeenCalled();
        });

        it('should handle error when currentMessages fails and proceed with empty array', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(throwError(() => new Error('test')));
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());

            component['requestSuggestion']();
            vi.advanceTimersByTime(0);

            expect(component['error']).toBe(IrisErrorMessageKey.SESSION_LOAD_FAILED);
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        });

        it('should set error if requestTutorSuggestion fails', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
            vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(throwError(() => new Error('test')));

            component['requestSuggestion']();
            vi.advanceTimersByTime(0);

            expect(component['error']).toBe(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });

        it('should not request suggestion when student is not tutor in course', () => {
            vi.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(false);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
            vi.spyOn(courseSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(courseSettings));
            vi.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
            vi.spyOn(chatService, 'currentError').mockReturnValue(of());
            vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            const requestTutorSuggestionSpy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([])));
            component.ngOnInit();
            vi.advanceTimersByTime(0);
            expect(requestTutorSuggestionSpy).not.toHaveBeenCalled();
        });

        // --- Additional tests for requestSuggestion branch coverage ---
        it('should not request suggestion if irisEnabled is false', () => {
            component['irisEnabled'] = false;
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });

        it('should not proceed if post is null', () => {
            componentRef.setInput('post', null as any);
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });

        it('should not request suggestion if last message is ARTIFACT and no new answer', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([{ id: 2, sender: 'ARTIFACT' } as IrisMessage]));
            vi.spyOn(component as any, 'checkForNewAnswerAndRequestSuggestion').mockReturnValue(false);
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['irisEnabled'] = true;
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });

        it('should process first non-empty emission and request suggestion', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([{ id: 1, sender: 'USER' } as IrisMessage])));
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['irisEnabled'] = true;
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).toHaveBeenCalled();
        });

        it('should recover from error in messages stream and still call suggestion', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(
                concat(
                    throwError(() => new Error('fail')),
                    of([{ id: 1, sender: 'USER' } as IrisMessage]),
                ),
            );
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['irisEnabled'] = true;
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).toHaveBeenCalled();
        });

        it('should emit SESSION_LOAD_FAILED on message fetch error', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(throwError(() => new Error('fetch error')));
            vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(component['error']).toBe(IrisErrorMessageKey.SESSION_LOAD_FAILED);
        });

        it('should not request suggestion if messages are only from LLM', () => {
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(chatService, 'currentMessages').mockReturnValue(of([{ id: 1, sender: 'LLM' } as IrisMessage]));
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion');
            component['irisEnabled'] = true;
            component['requestSuggestion']();
            vi.advanceTimersByTime(0);
            expect(spy).not.toHaveBeenCalled();
        });
    });

    describe('switchSuggestion and updateArrowDisabled', () => {
        beforeEach(() => {
            // Simulate a list of suggestions (id: 1, 2, 3)
            component.suggestions = [{ id: 1, sender: 'ARTIFACT' } as IrisMessage, { id: 2, sender: 'ARTIFACT' } as IrisMessage, { id: 3, sender: 'ARTIFACT' } as IrisMessage];
        });

        it('should not switch if suggestion or suggestions is undefined', () => {
            component.suggestion = undefined;
            component.suggestions = undefined as any;
            const initialUp = component.upDisabled;
            const initialDown = component.downDisabled;
            component.switchSuggestion(true);
            expect(component.suggestion).toBeUndefined();
            expect(component.upDisabled).toBe(initialUp);
            expect(component.downDisabled).toBe(initialDown);
        });

        it('should not switch if currentIndex is -1', () => {
            component.suggestion = { id: 99, sender: 'ARTIFACT' } as IrisMessage;
            component.switchSuggestion(true);
            expect(component.suggestion.id).toBe(99);
        });

        it('should switch to next suggestion when up=true', () => {
            component.suggestion = component.suggestions[0]; // id: 1
            component.switchSuggestion(true);
            expect(component.suggestion.id).toBe(2);
            expect(component.downDisabled).toBe(false); // index 1
            expect(component.upDisabled).toBe(false);
        });

        it('should switch to previous suggestion when up=false', () => {
            component.suggestion = component.suggestions[2]; // id: 3
            component.switchSuggestion(false);
            expect(component.suggestion.id).toBe(2);
            expect(component.downDisabled).toBe(false);
            expect(component.upDisabled).toBe(false);
        });

        it('should not switch when at first element and up=false', () => {
            component.suggestion = component.suggestions[0];
            component.switchSuggestion(false);
            expect(component.suggestion.id).toBe(1);
            expect(component.downDisabled).toBe(true);
            expect(component.upDisabled).toBe(false);
        });

        it('should not switch when at last element and up=true', () => {
            component.suggestion = component.suggestions[2];
            component.switchSuggestion(true);
            expect(component.suggestion.id).toBe(3);
            expect(component.downDisabled).toBe(false);
            expect(component.upDisabled).toBe(true);
        });

        it('should update arrow states for a single-element suggestions list', () => {
            component.suggestions = [{ id: 10, sender: 'ARTIFACT' } as IrisMessage];
            component.suggestion = component.suggestions[0];
            component['updateArrowDisabled'](0);
            expect(component.downDisabled).toBe(true);
            expect(component.upDisabled).toBe(true);
        });

        it('should update arrow states for the first index', () => {
            component['updateArrowDisabled'](0);
            expect(component.downDisabled).toBe(true);
            expect(component.upDisabled).toBe(false);
        });

        it('should update arrow states for a middle index', () => {
            component['updateArrowDisabled'](1);
            expect(component.downDisabled).toBe(false);
            expect(component.upDisabled).toBe(false);
        });

        it('should update arrow states for the last index', () => {
            component['updateArrowDisabled'](2);
            expect(component.downDisabled).toBe(false);
            expect(component.upDisabled).toBe(true);
        });
    });

    describe('checkForNewAnswerAndRequestSuggestion', () => {
        it('should return false if no post', () => {
            componentRef.setInput('post', undefined as any);
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(false);
        });

        it('should return false if post has no answers', () => {
            componentRef.setInput('post', { id: 1, answers: [] } as any);
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(false);
        });

        it('should return false if no suggestions', () => {
            component.suggestions = [];
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(false);
        });

        it('should return false if lastSuggestion or lastSuggestion.sentAt is missing', () => {
            component.suggestions = [{} as any];
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(false);
        });

        it('should return false if latest answer is before last suggestion', () => {
            component.suggestions = [{ id: 10, sender: 'ARTIFACT', sentAt: dayjs('2024-07-01T10:00:00Z').toISOString() } as any];
            componentRef.setInput('post', { id: 1, answers: [{ id: 1, creationDate: dayjs('2024-07-01T09:00:00Z').toISOString() }] } as any);
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(false);
        });

        it('should return true if latest answer is after last suggestion', () => {
            component.suggestions = [{ id: 10, sender: 'ARTIFACT', sentAt: dayjs('2024-07-01T10:00:00Z').toISOString() } as any];
            componentRef.setInput('post', { id: 1, answers: [{ id: 1, creationDate: dayjs('2024-07-01T11:00:00Z').toISOString() }] } as any);
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(true);
        });

        it('should compare latest answer when there are multiple answers', () => {
            component.suggestions = [{ id: 10, sender: 'ARTIFACT', sentAt: dayjs('2024-07-01T10:00:00Z').toISOString() } as any];
            componentRef.setInput('post', {
                id: 1,
                answers: [
                    { id: 1, creationDate: dayjs('2024-07-01T09:00:00Z').toISOString() },
                    { id: 2, creationDate: dayjs('2024-07-01T12:00:00Z').toISOString() },
                    { id: 3, creationDate: dayjs('2024-07-01T11:00:00Z').toISOString() },
                ],
            } as any);
            expect((component as any).checkForNewAnswerAndRequestSuggestion()).toBe(true);
        });
    });

    describe('requestSuggestion integration with checkForNewAnswerAndRequestSuggestion', () => {
        beforeEach(() => {
            componentRef.setInput('post', { id: 1, answers: [{ id: 1, creationDate: dayjs('2024-07-01T12:00:00Z').toISOString() }] } as any);
            component.suggestions = [{ id: 10, sender: 'ARTIFACT', sentAt: dayjs('2024-07-01T10:00:00Z').toISOString() } as any];
            vi.spyOn(component as any, 'checkForNewAnswerAndRequestSuggestion').mockReturnValue(true);
            vi.spyOn(component['chatService'], 'currentSessionId').mockReturnValue(of(123));
            vi.spyOn(component['chatService'], 'currentMessages').mockReturnValue(of([]));
            vi.spyOn(component['chatService'], 'requestTutorSuggestion').mockReturnValue(of());
            setUpForRequestSuggestion();
        });

        it('should call checkForNewAnswerAndRequestSuggestion in requestSuggestion', () => {
            const checkSpy = vi.spyOn(component as any, 'checkForNewAnswerAndRequestSuggestion');
            (component as any).requestSuggestion();
            vi.advanceTimersByTime(0);
            expect(checkSpy).toHaveBeenCalled();
        });

        it('should call requestTutorSuggestion if checkForNewAnswerAndRequestSuggestion returns true', () => {
            (component as any).checkForNewAnswerAndRequestSuggestion.mockReturnValue(true);
            const reqSpy = vi.spyOn(component['chatService'], 'requestTutorSuggestion');
            (component as any).requestSuggestion();
            vi.advanceTimersByTime(0);
            expect(reqSpy).toHaveBeenCalled();
        });

        it('should not call requestTutorSuggestion if checkForNewAnswerAndRequestSuggestion returns false and last message from LLM/ARTIFACT', () => {
            (component as any).checkForNewAnswerAndRequestSuggestion.mockReturnValue(false);
            vi.spyOn(component['chatService'], 'currentMessages').mockReturnValue(of([{ sender: 'LLM' } as any]));
            const reqSpy = vi.spyOn(component['chatService'], 'requestTutorSuggestion');
            (component as any).requestSuggestion();
            vi.advanceTimersByTime(0);
            expect(reqSpy).not.toHaveBeenCalled();
        });
    });

    describe('userRequestedNewSuggestion', () => {
        it('should call requestTutorSuggestion on success', () => {
            const spy = vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component.userRequestedNewSuggestion();
            expect(spy).toHaveBeenCalled();
        });

        it('should set error when requestTutorSuggestion fails', () => {
            vi.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(throwError(() => new Error('fail')));
            component.userRequestedNewSuggestion();
            expect(component['error']).toBe(IrisErrorMessageKey.SEND_MESSAGE_FAILED);
        });
    });

    describe('subscribeToIrisActivation', () => {
        it('should call requestSuggestion when active and session id is available', () => {
            vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
            vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(321));
            const spy = vi.spyOn(component as any, 'requestSuggestion');

            (component as any).subscribeToIrisActivation();

            expect(spy).toHaveBeenCalled();
        });
    });

    function setUpForRequestSuggestion() {
        vi.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        vi.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
        vi.spyOn(chatService, 'currentError').mockReturnValue(of());
        vi.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
        component['irisEnabled'] = true;
        vi.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        vi.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
    }
});

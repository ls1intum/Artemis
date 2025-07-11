import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { concat, of, throwError } from 'rxjs';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProvider } from 'ng-mocks';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { AccountService } from 'app/core/auth/account.service';
import { IrisStatusService } from 'app/iris/overview/services/iris-status.service';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { TranslateService } from '@ngx-translate/core';

describe('TutorSuggestionComponent', () => {
    let component: TutorSuggestionComponent;
    let componentRef: ComponentRef<TutorSuggestionComponent>;
    let fixture: ComponentFixture<TutorSuggestionComponent>;
    let irisSettingsService: IrisSettingsService;
    let profileService: ProfileService;
    let chatService: IrisChatService;
    let accountService: AccountService;
    let featureToggleService: FeatureToggleService;
    let irisStatusService: IrisStatusService;
    let translateService: TranslateService;
    const irisSettings = mockSettings();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorSuggestionComponent],
            providers: [
                MockProvider(IrisChatService),
                MockProvider(IrisSettingsService),
                MockProvider(ProfileService),
                MockProvider(AccountService),
                MockProvider(IrisStatusService),
                MockProvider(FeatureToggleService),
                MockProvider(TranslateService),
            ],
        }).compileComponents();

        irisStatusService = TestBed.inject(IrisStatusService);
        jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

        fixture = TestBed.createComponent(TutorSuggestionComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;

        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        chatService = TestBed.inject(IrisChatService);
        accountService = TestBed.inject(AccountService);
        featureToggleService = TestBed.inject(FeatureToggleService);
        translateService = TestBed.inject(TranslateService);

        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        jest.spyOn(translateService, 'get').mockReturnValue(of(''));
        (translateService as any).onLangChange = of({ lang: 'en', translations: {} });
        jest.spyOn(translateService, 'stream').mockReturnValue(of(''));
        (translateService as any).onTranslationChange = of({ lang: 'en', translations: {} });
        (translateService as any).onDefaultLangChange = of({ lang: 'en', translations: {} });

        componentRef.setInput('post', { id: 1 } as any);
        componentRef.setInput('course', { id: 1 } as any);
    });

    it('should initialize and switch chat service if IRIS is enabled', () => {
        jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const switchToSpy = jest.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        const getFeatureToggleSpy = jest.spyOn(featureToggleService, 'getFeatureToggleActive');
        const getActiveStatusSpy = jest.spyOn(irisStatusService, 'getActiveStatus');
        fixture.detectChanges();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
        expect(getFeatureToggleSpy).toHaveBeenCalledWith(FeatureToggle.TutorSuggestions);
        expect(getActiveStatusSpy).toHaveBeenCalled();
    });

    it('should initialize properly in ngOnInit and load settings', fakeAsync(() => {
        jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        const profileServiceMock = jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const switchToSpy = jest.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
        fixture.detectChanges();
        tick();
        expect(profileServiceMock).toHaveBeenCalledOnce();
        expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    }));

    describe('should set irisEnabled to', () => {
        it('false if Iris profile is not enabled', fakeAsync(() => {
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(false);
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBeFalse();
        }));

        it('false if settings are not available', fakeAsync(() => {
            jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(undefined));
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBeFalse();
        }));

        it('false if course id is not available', fakeAsync(() => {
            componentRef.setInput('course', null);
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBeFalse();
        }));

        it('false if post id is not available', fakeAsync(() => {
            componentRef.setInput('post', null);
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBeFalse();
        }));

        it('true if all conditions are met', fakeAsync(() => {
            jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
            jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
            jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
            fixture.detectChanges();
            tick();
            expect(component['irisEnabled']).toBeTrue();
        }));

        it('should set irisEnabled to false if feature toggle is disabled', fakeAsync(() => {
            jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(false));
            fixture.detectChanges();
            tick();
            expect(component['irisEnabled']).toBeFalse();
        }));
    });

    it('should call requestSuggestion when sessionId emits in ngOnChanges', fakeAsync(() => {
        const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));

        jest.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
        component['irisEnabled'] = true;
        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
        component.ngOnChanges();
        tick();
        expect(requestTutorSuggestionSpy).toHaveBeenCalled();
    }));

    it('should not call requestSuggestion if iris is active but no sessionId', fakeAsync(() => {
        jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(undefined));
        jest.spyOn(irisStatusService, 'getActiveStatus').mockReturnValue(of(true));
        const requestSuggestionSpy = jest.spyOn(component as any, 'requestSuggestion');
        (component as any).subscribeToIrisActivation();
        tick();
        expect(requestSuggestionSpy).not.toHaveBeenCalled();
    }));

    it('should unsubscribe from all services on destroy', () => {
        jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(true);
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));

        jest.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([])));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
        component.ngOnInit();
        const irisUnsubSpy = jest.spyOn(component['irisSettingsSubscription'], 'unsubscribe');
        const msgUnsubSpy = jest.spyOn(component['messagesSubscription'], 'unsubscribe');
        const stagesUnsubSpy = jest.spyOn(component['stagesSubscription'], 'unsubscribe');
        const errorUnsubSpy = jest.spyOn(component['errorSubscription'], 'unsubscribe');
        const tutorSuggestionUnsubSpy = jest.spyOn(component['tutorSuggestionSubscription'], 'unsubscribe');

        component.ngOnDestroy();

        expect(irisUnsubSpy).toHaveBeenCalled();
        expect(msgUnsubSpy).toHaveBeenCalled();
        expect(stagesUnsubSpy).toHaveBeenCalled();
        expect(errorUnsubSpy).toHaveBeenCalled();
        expect(tutorSuggestionUnsubSpy).toHaveBeenCalled();
    });

    it('should update suggestion in fetchMessages if last message is an Artifact', fakeAsync(() => {
        const mockMessages = [{ id: 1, sender: 'USER' } as IrisMessage, { id: 2, sender: 'ARTIFACT' } as IrisMessage];
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        component['fetchMessages']();
        tick();
        expect(component.suggestion).toEqual(mockMessages[1]);
        expect(component.messages).toEqual(mockMessages);
    }));

    describe('requestSuggestion', () => {
        it('should request suggestion when there are no messages after error fallback', fakeAsync(() => {
            jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(
                concat(
                    throwError(() => new Error('empty')),
                    of([]),
                ),
            );
            const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            tick();
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        }));

        it('should request suggestion when second message emission contains LLM message', fakeAsync(() => {
            jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([]), of([])));
            const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            tick();
            expect(requestTutorSuggestionSpy).toHaveBeenCalled();
        }));

        it('should not request suggestion when last message is not from LLM', fakeAsync(() => {
            jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            const mockMessages = [{ id: 1, sender: 'USER' }] as IrisMessage[];
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
            const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            component['requestSuggestion']();
            tick();
            expect(requestTutorSuggestionSpy).not.toHaveBeenCalled();
        }));

        it('should not request suggestion when student is not tutor in course', fakeAsync(() => {
            jest.spyOn(accountService, 'isAtLeastTutorInCourse').mockReturnValue(false);
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
            jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
            jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
            jest.spyOn(chatService, 'currentError').mockReturnValue(of());
            jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
            jest.spyOn(chatService, 'currentSessionId').mockReturnValue(of(123));
            jest.spyOn(chatService, 'currentMessages').mockReturnValue(concat(of([])));
            component.ngOnInit();
            tick();
            expect(requestTutorSuggestionSpy).not.toHaveBeenCalled();
        }));
    });
});

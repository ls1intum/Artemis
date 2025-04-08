import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { of } from 'rxjs';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProvider } from 'ng-mocks';
import { IrisMessage } from 'app/iris/shared/entities/iris-message.model';
import { ChatServiceMode, IrisChatService } from 'app/iris/overview/services/iris-chat.service';
import { mockSettings } from 'test/helpers/mocks/iris/mock-settings';
import { AccountService } from 'app/core/auth/account.service';

describe('TutorSuggestionComponent', () => {
    let component: TutorSuggestionComponent;
    let componentRef: ComponentRef<TutorSuggestionComponent>;
    let fixture: ComponentFixture<TutorSuggestionComponent>;
    let irisSettingsService: IrisSettingsService;
    let profileService: ProfileService;
    let chatService: IrisChatService;
    const irisSettings = mockSettings();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorSuggestionComponent],
            providers: [MockProvider(IrisChatService), MockProvider(IrisSettingsService), MockProvider(ProfileService), MockProvider(AccountService)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorSuggestionComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;

        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
        chatService = TestBed.inject(IrisChatService);

        componentRef.setInput('post', { id: 1 } as any);
        componentRef.setInput('course', { id: 1 } as any);
    });

    it('should initialize and switch chat service if IRIS is enabled', () => {
        jest.spyOn(component['accountService'], 'isAtLeastTutorInCourse').mockReturnValue(true);
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const switchToSpy = jest.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        fixture.detectChanges();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    });

    it('should initialize properly in ngOnInit and load settings', fakeAsync(() => {
        jest.spyOn(component['accountService'], 'isAtLeastTutorInCourse').mockReturnValue(true);
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        const profileServiceMock = jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const switchToSpy = jest.spyOn(chatService, 'switchTo').mockReturnValue(undefined);
        fixture.detectChanges();
        tick();
        expect(profileServiceMock).toHaveBeenCalledOnce();
        expect(getCourseSettingsSpy).toHaveBeenCalledOnce();
        expect(switchToSpy).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    }));

    describe('should set irisEnabled to', () => {
        it('false if Iris profile is not enabled', fakeAsync(() => {
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(false);

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

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBeFalse();
        }));

        it('true if all conditions are met', fakeAsync(() => {
            jest.spyOn(component['accountService'], 'isAtLeastTutorInCourse').mockReturnValue(true);
            jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
            jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
            fixture.detectChanges();
            tick();
            expect(component['irisEnabled']).toBeTrue();
        }));
    });

    it('should call requestSuggestion when sessionId emits in ngOnChanges', fakeAsync(() => {
        const requestTutorSuggestionSpy = jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        (chatService as any).sessionId$ = of(123);
        component['irisEnabled'] = true;
        component.ngOnChanges();
        tick();
        expect(requestTutorSuggestionSpy).toHaveBeenCalled();
    }));

    it('should unsubscribe from all services on destroy', () => {
        jest.spyOn(component['accountService'], 'isAtLeastTutorInCourse').mockReturnValue(true);
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        jest.spyOn(irisSettingsService, 'getCombinedCourseSettings').mockReturnValue(of(irisSettings));
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        jest.spyOn(chatService, 'requestTutorSuggestion').mockReturnValue(of());
        (chatService as any).sessionId$ = of(123);
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

    it('should update suggestion in fetchMessages if last message is from LLM', fakeAsync(() => {
        const mockMessages = [{ id: 1, sender: 'USER' } as IrisMessage, { id: 2, sender: 'LLM' } as IrisMessage];
        jest.spyOn(chatService, 'currentMessages').mockReturnValue(of(mockMessages));
        jest.spyOn(chatService, 'currentStages').mockReturnValue(of([]));
        jest.spyOn(chatService, 'currentError').mockReturnValue(of());
        component['fetchMessages']();
        tick();
        expect(component.suggestion).toEqual(mockMessages[1]);
        expect(component.messages).toEqual(mockMessages);
    }));

    it('should not proceed in ngOnInit if user is not at least tutor in course', fakeAsync(() => {
        const isAtLeastTutorSpy = jest.spyOn(component['accountService'], 'isAtLeastTutorInCourse').mockReturnValue(false);
        const getCourseSettingsSpy = jest.spyOn(component['irisSettingsService'], 'getCombinedCourseSettings');
        fixture.detectChanges();
        tick();
        expect(isAtLeastTutorSpy).toHaveBeenCalled();
        expect(getCourseSettingsSpy).not.toHaveBeenCalled();
    }));
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { IrisChatService } from 'app/iris/overview/iris-chat.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { of, Subject } from 'rxjs';
import { ChatServiceMode } from 'app/iris/overview/iris-chat.service';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';

describe('TutorSuggestionComponent', () => {
    let component: TutorSuggestionComponent;
    let componentRef: ComponentRef<TutorSuggestionComponent>;
    let fixture: ComponentFixture<TutorSuggestionComponent>;
    let chatServiceMock: any;
    let profileServiceMock: any;
    let irisSettingsServiceMock: any;

    beforeEach(async () => {
        chatServiceMock = {
            switchTo: jest.fn(),
            sessionId$: new Subject<number>(),
            currentMessages: jest.fn().mockReturnValue(of([])),
            requestTutorSuggestion: jest.fn().mockReturnValue(of(undefined)),
        };

        profileServiceMock = {
            getProfileInfo: jest.fn().mockReturnValue(of({ activeProfiles: ['iris'] })),
        };

        irisSettingsServiceMock = {
            getCombinedCourseSettings: jest.fn().mockReturnValue(of({ irisTutorSuggestionSettings: { enabled: true } })),
        };

        await TestBed.configureTestingModule({
            imports: [TutorSuggestionComponent],
            providers: [
                { provide: IrisChatService, useValue: chatServiceMock },
                { provide: ProfileService, useValue: profileServiceMock },
                { provide: IrisSettingsService, useValue: irisSettingsServiceMock },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorSuggestionComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;

        componentRef.setInput('post', { id: 1 } as any);
        componentRef.setInput('course', { id: 1 } as any);

        fixture.detectChanges(); // triggers ngOnInit
    });

    it('should initialize and switch chat service if IRIS is enabled', () => {
        expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    });

    it('should initialize properly in ngOnInit and load settings', fakeAsync(() => {
        fixture.detectChanges();
        tick();
        expect(profileServiceMock.getProfileInfo).toHaveBeenCalled();
        expect(irisSettingsServiceMock.getCombinedCourseSettings).toHaveBeenCalledWith(1);
        expect(chatServiceMock.switchTo).toHaveBeenCalledWith(ChatServiceMode.TUTOR_SUGGESTION, 1);
    }));

    describe('should set irisEnabled to ', () => {
        it('false if Iris profile is not enabled', fakeAsync(() => {
            profileServiceMock.getProfileInfo.mockReturnValue(of({ activeProfiles: [] }));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBe(false);
        }));

        it('false if settings are not available', fakeAsync(() => {
            irisSettingsServiceMock.getCombinedCourseSettings.mockReturnValue(of(null));

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);
            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBe(false);
        }));

        it('false if course id is not available', fakeAsync(() => {
            componentRef.setInput('course', null);

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('post', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBe(false);
        }));

        it('false if post id is not available', fakeAsync(() => {
            componentRef.setInput('post', null);

            fixture = TestBed.createComponent(TutorSuggestionComponent);
            component = fixture.componentInstance;
            componentRef = fixture.componentRef;

            componentRef.setInput('course', { id: 1 } as any);

            fixture.detectChanges();
            tick();

            expect(component['irisEnabled']).toBe(false);
        }));

        it('true if all conditions are met', fakeAsync(() => {
            fixture.detectChanges();
            tick();
            expect(component['irisEnabled']).toBe(true);
        }));
    });

    it('should call requestSuggestion when sessionId emits in ngOnChanges', fakeAsync(() => {
        component['irisEnabled'] = true;
        component.ngOnChanges();
        chatServiceMock.sessionId$.next(123);
        tick();
        expect(chatServiceMock.requestTutorSuggestion).toHaveBeenCalled();
    }));

    it('should react to emitted sessionId', fakeAsync(() => {
        component['irisEnabled'] = true;
        component.ngOnChanges();
        chatServiceMock.sessionId$.next(456);
        tick();
        expect(chatServiceMock.requestTutorSuggestion).toHaveBeenCalled();
    }));

    it('should unsubscribe from all services on destroy', () => {
        const profileUnsubSpy = jest.spyOn(component['profileSubscription'], 'unsubscribe');
        const irisUnsubSpy = jest.spyOn(component['irisSettingsSubscription'], 'unsubscribe');
        const msgUnsubSpy = jest.spyOn(component['messagesSubscription'], 'unsubscribe');

        component.ngOnDestroy();

        expect(profileUnsubSpy).toHaveBeenCalled();
        expect(irisUnsubSpy).toHaveBeenCalled();
        expect(msgUnsubSpy).toHaveBeenCalled();
    });

    it('should update suggestion in fetchMessages if last message is from LLM', fakeAsync(() => {
        const mockMessages = [
            { id: 1, sender: 'USER' },
            { id: 2, sender: 'LLM' },
        ];
        chatServiceMock.currentMessages.mockReturnValue(of(mockMessages));
        component['fetchMessages']();
        tick();
        expect(component.suggestion).toEqual(mockMessages[1]);
        expect(component.messages).toEqual(mockMessages);
    }));
});

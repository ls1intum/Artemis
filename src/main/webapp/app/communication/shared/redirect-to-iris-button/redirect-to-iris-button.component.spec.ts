import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ComponentRef } from '@angular/core';
import { RedirectToIrisButtonComponent } from 'app/communication/shared/redirect-to-iris-button/redirect-to-iris-button.component';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { mockCourseSettings } from 'test/helpers/mocks/iris/mock-settings';
import { of, throwError } from 'rxjs';
import { MockProvider } from 'ng-mocks';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { IrisLogoSize } from 'app/iris/overview/iris-logo/iris-logo.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

describe('RedirectToIrisButtonComponent', () => {
    let component: RedirectToIrisButtonComponent;
    let fixture: ComponentFixture<RedirectToIrisButtonComponent>;
    let componentRef: ComponentRef<RedirectToIrisButtonComponent>;
    let irisSettingsService: IrisSettingsService;
    let profileService: ProfileService;
    const irisSettings = mockCourseSettings();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                MockProvider(ProfileService),
                MockProvider(IrisSettingsService),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(RedirectToIrisButtonComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        fixture.detectChanges();
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
    });

    it('should have default values', () => {
        expect(component.buttonLoading()).toBeFalse();
    });

    it('should set the correct logo size to TEXT when using IrisLogoComponent', () => {
        expect(component.TEXT).toBe(IrisLogoSize.TEXT);
    });

    it('should show loading spinner when buttonLoading is true', () => {
        componentRef.setInput('buttonLoading', true);
        fixture.detectChanges();

        expect(component.buttonLoading()).toBeTrue();
        expect(component.faCircleNotch).toBe(faCircleNotch);
    });

    it('should be enabled for exercise chats if Iris is activated for the exercise', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(1, true)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeTrue();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    }));

    it('should be enabled for lecture chats if Iris is activated for the lecture', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(56, true)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 56, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.LECTURE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeTrue();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(56);
    }));

    it('should be enabled for general chat if Iris is activated for the course chat', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(64, true)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 64, studentCourseAnalyticsDashboardEnabled: true } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeTrue();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(64);
    }));

    it('should be disabled for exercise chats if Iris is disabled for the exercise', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(1, false)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeFalse();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    }));

    it('should be disabled for general chats if Iris is disabled for the course chat', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(42, false)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 42, studentCourseAnalyticsDashboardEnabled: true } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeFalse();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(42);
    }));

    it('should be disabled for lecture chats if Iris is disabled for the lecture', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(56, false)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 56, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.LECTURE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeFalse();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(56);
    }));

    it('should handle errors when retrieving Iris settings', fakeAsync(() => {
        const getSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(throwError(() => new Error('Failed to fetch settings')));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeFalse();
        expect(getSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    }));

    it('should be displayed for general chats regardless whether Student Course Analytics Dashboard is disabled', fakeAsync(() => {
        const getCourseSettingsSpy = jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(42, true)));
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 42, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeTrue();
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(42);
    }));

    it('should add the message as payload when redirectToIrisButton is pressed', fakeAsync(() => {
        component.channelSubTypeReferenceRouterLink = '/courses/1/exercises/1';
        componentRef.setInput('question', 'test');

        const routerNavigateSpy = jest.spyOn(component.router, 'navigate').mockImplementation(() => Promise.resolve(true));
        component.redirectToIris();
        const correctLink = '/courses/1/exercises/1';

        expect(routerNavigateSpy).toHaveBeenCalledWith([correctLink], { queryParams: { irisQuestion: component.question() } });
    }));

    it('should be displayed if profile Iris is set', fakeAsync(() => {
        const getProfileActiveSpy = jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        setupIrisSettingsAndConversation();

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeTrue();
        expect(getProfileActiveSpy).toHaveBeenCalledWith('iris');
    }));

    it('should not be displayed if profile Iris is set', fakeAsync(() => {
        const getProfileActiveSpy = jest.spyOn(profileService, 'isProfileActive').mockReturnValue(false);
        setupIrisSettingsAndConversation();

        component.ngOnInit();
        tick();

        expect(component.irisEnabled()).toBeFalse();
        expect(getProfileActiveSpy).toHaveBeenCalledWith('iris');
    }));

    function setupIrisSettingsAndConversation() {
        jest.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(irisSettings));
        componentRef.setInput('course', { id: 1 } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));
    }

    it('should ignore undefined conversations', fakeAsync(() => {
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const spyCheckIrisSettings = jest.spyOn(component as any, 'checkIrisSettings');
        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(undefined));

        component.ngOnInit();
        tick();

        expect(spyCheckIrisSettings).not.toHaveBeenCalled();
    }));

    it('should call checkIrisSettings on new conversation with different id', fakeAsync(() => {
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const spyCheckIrisSettings = jest.spyOn(component as any, 'checkIrisSettings');

        const firstConversation = { id: 1, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        const secondConversation = { id: 2, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(firstConversation, secondConversation));

        component.ngOnInit();
        tick();

        expect(spyCheckIrisSettings).toHaveBeenCalledTimes(2);
    }));

    it('should not call checkIrisSettings if conversation id stays the same', fakeAsync(() => {
        jest.spyOn(profileService, 'isProfileActive').mockReturnValue(true);
        const spyCheckIrisSettings = jest.spyOn(component as any, 'checkIrisSettings');

        const sameConversation = { id: 1, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        jest.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(sameConversation, sameConversation));

        component.ngOnInit();
        tick();

        expect(spyCheckIrisSettings).toHaveBeenCalledOnce();
    }));
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
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
    setupTestBed({ zoneless: true });

    let component: RedirectToIrisButtonComponent;
    let fixture: ComponentFixture<RedirectToIrisButtonComponent>;
    let componentRef: ComponentRef<RedirectToIrisButtonComponent>;
    let irisSettingsService: IrisSettingsService;
    let profileService: ProfileService;
    const irisSettings = mockCourseSettings();

    beforeEach(async () => {
        vi.useFakeTimers();
        await TestBed.configureTestingModule({
            providers: [
                { provide: MetisService, useClass: MockMetisService },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
                MockProvider(ProfileService),
                MockProvider(IrisSettingsService),
            ],
        });

        fixture = TestBed.createComponent(RedirectToIrisButtonComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        fixture.detectChanges();
        irisSettingsService = TestBed.inject(IrisSettingsService);
        profileService = TestBed.inject(ProfileService);
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it('should have default values', () => {
        expect(component.buttonLoading()).toBe(false);
    });

    it('should set the correct logo size to TEXT when using IrisLogoComponent', () => {
        expect(component.TEXT).toBe(IrisLogoSize.TEXT);
    });

    it('should show loading spinner when buttonLoading is true', () => {
        componentRef.setInput('buttonLoading', true);
        fixture.detectChanges();

        expect(component.buttonLoading()).toBe(true);
        expect(component.faCircleNotch).toBe(faCircleNotch);
    });

    it('should be enabled for exercise chats if Iris is activated for the exercise', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(1, true)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(true);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should be enabled for lecture chats if Iris is activated for the lecture', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(56, true)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 56, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.LECTURE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(true);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(56);
    });

    it('should be enabled for general chat if Iris is activated for the course chat', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(64, true)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 64, studentCourseAnalyticsDashboardEnabled: true } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(true);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(64);
    });

    it('should be disabled for exercise chats if Iris is disabled for the exercise', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(1, false)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(false);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should be disabled for general chats if Iris is disabled for the course chat', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(42, false)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 42, studentCourseAnalyticsDashboardEnabled: true } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(false);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(42);
    });

    it('should be disabled for lecture chats if Iris is disabled for the lecture', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(56, false)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 56, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.LECTURE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(false);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(56);
    });

    it('should handle errors when retrieving Iris settings', () => {
        const getSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(throwError(() => new Error('Failed to fetch settings')));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 1, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(false);
        expect(getSettingsSpy).toHaveBeenCalledExactlyOnceWith(1);
    });

    it('should be displayed for general chats regardless whether Student Course Analytics Dashboard is disabled', () => {
        const getCourseSettingsSpy = vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(mockCourseSettings(42, true)));
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);

        componentRef.setInput('course', { id: 42, studentCourseAnalyticsDashboardEnabled: false } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.GENERAL,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(true);
        expect(getCourseSettingsSpy).toHaveBeenCalledExactlyOnceWith(42);
    });

    it('should add the message as payload when redirectToIrisButton is pressed', () => {
        component.channelSubTypeReferenceRouterLink = '/courses/1/exercises/1';
        componentRef.setInput('question', 'test');

        const routerNavigateSpy = vi.spyOn(component.router, 'navigate').mockImplementation(() => Promise.resolve(true));
        component.redirectToIris();
        const correctLink = '/courses/1/exercises/1';

        expect(routerNavigateSpy).toHaveBeenCalledWith([correctLink], { queryParams: { irisQuestion: component.question() } });
    });

    it('should be displayed if Iris module feature is enabled', () => {
        const getModuleFeatureActiveSpy = vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        setupIrisSettingsAndConversation();

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(true);
        expect(getModuleFeatureActiveSpy).toHaveBeenCalledWith('iris');
    });

    it('should not be displayed if Iris module feature is disabled', () => {
        const getModuleFeatureActiveSpy = vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
        setupIrisSettingsAndConversation();

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(component.irisEnabled()).toBe(false);
        expect(getModuleFeatureActiveSpy).toHaveBeenCalledWith('iris');
    });

    function setupIrisSettingsAndConversation() {
        vi.spyOn(irisSettingsService, 'getCourseSettingsWithRateLimit').mockReturnValue(of(irisSettings));
        componentRef.setInput('course', { id: 1 } as Course);

        const mockChannelDTO = {
            type: ConversationType.CHANNEL,
            subType: ChannelSubType.EXERCISE,
            subTypeReferenceId: 42,
        } as ConversationDTO;

        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(mockChannelDTO));
    }

    it('should ignore undefined conversations', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        const spyCheckIrisSettings = vi.spyOn(component as any, 'checkIrisSettings');
        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(undefined));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(spyCheckIrisSettings).not.toHaveBeenCalled();
    });

    it('should call checkIrisSettings on new conversation with different id', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        const spyCheckIrisSettings = vi.spyOn(component as any, 'checkIrisSettings');

        const firstConversation = { id: 1, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        const secondConversation = { id: 2, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(firstConversation, secondConversation));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(spyCheckIrisSettings).toHaveBeenCalledTimes(2);
    });

    it('should not call checkIrisSettings if conversation id stays the same', () => {
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(true);
        const spyCheckIrisSettings = vi.spyOn(component as any, 'checkIrisSettings');

        const sameConversation = { id: 1, type: ConversationType.CHANNEL, subType: ChannelSubType.EXERCISE } as ConversationDTO;
        vi.spyOn(component.metisConversationService, 'activeConversation$', 'get').mockReturnValue(of(sameConversation, sameConversation));

        component.ngOnInit();
        vi.advanceTimersByTime(0);

        expect(spyCheckIrisSettings).toHaveBeenCalledOnce();
    });
});

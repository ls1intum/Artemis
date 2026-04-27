import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TutorialGroupDetailAccessLevel, TutorialGroupDetailComponent } from './tutorial-group-detail.component';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ScaleType } from '@swimlane/ngx-charts';
import { of, throwError } from 'rxjs';
import { OneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { User } from 'app/core/user/user.model';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { TutorialGroupDetailData as RawTutorialGroupDetailData } from 'app/openapi/model/tutorialGroupDetailData';
import { TutorialGroupSession as RawTutorialGroupSession } from 'app/openapi/model/tutorialGroupSession';
import { CreateOrUpdateTutorialGroupSessionRequest } from 'app/openapi/model/createOrUpdateTutorialGroupSessionRequest';
import { ConfirmationService } from 'primeng/api';
import {
    TutorialSessionCreateOrEditModalComponent,
    UpdateTutorialGroupSessionData,
} from 'app/tutorialgroup/manage/tutorial-group-session-create-or-edit-modal/tutorial-session-create-or-edit-modal.component';

describe('TutorialGroupDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialGroupDetailComponent;
    let fixture: ComponentFixture<TutorialGroupDetailComponent>;

    const mockTranslateService = new MockTranslateService();
    mockTranslateService.use('en');
    const mockAccountService = new MockAccountService();
    mockAccountService.userIdentity.set(new User(undefined, 'artemis_admin'));
    const mockRouter = new MockRouter();

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TutorialGroupDetailComponent, MockDirective(TranslateDirective), MockDirective(RouterLink)],
            providers: [
                { provide: TranslateService, useValue: mockTranslateService },
                { provide: AccountService, useValue: mockAccountService },
                { provide: Router, useValue: mockRouter },
                MockProvider(OneToOneChatService),
                MockProvider(AlertService),
                MockProvider(LectureService),
                MockProvider(ActivatedRoute),
            ],
        }).compileComponents();

        const lectureService = TestBed.inject(LectureService);
        lectureService.currentTutorialLectureId = 3;

        fixture = TestBed.createComponent(TutorialGroupDetailComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('isMessagingEnabled', true);
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.STUDENT);
    });

    beforeAll(() => {
        process.env.TZ = 'Europe/Berlin'; // pin TZ
        vi.useFakeTimers();
        // pick a winter date (no DST jump). Any fixed date works, just be consistent.
        vi.setSystemTime(new Date('2025-01-14T10:00:00+01:00'));
    });

    afterAll(() => {
        vi.useRealTimers();
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createRawTutorialGroupSessionDTO(overrides: Partial<RawTutorialGroupSession> = {}): RawTutorialGroupSession {
        return {
            id: 1,
            start: dayjs('2025-01-15T13:00:00+01:00').toISOString(),
            end: dayjs('2025-01-15T15:00:00+01:00').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            isCancelledByFreePeriod: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
            ...overrides,
        };
    }

    function createTutorialGroupDetailData(overrides: Partial<RawTutorialGroupDetailData> = {}): TutorialGroupDetailData {
        return new TutorialGroupDetailData({
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            tutorName: 'Marlon Nienaber',
            tutorLogin: 'gx89tum',
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            additionalInformation: undefined,
            groupChannelId: undefined,
            tutorChatId: undefined,
            ...overrides,
        });
    }

    // test validating display of controls based on access control level and available data

    it('should display no conversation links if messaging disabled', () => {
        fixture.componentRef.setInput('isMessagingEnabled', false);
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(tutorChatLink).toBeNull();
        expect(tutorChatButton).toBeNull();
        expect(groupChannelLink).toBeNull();
    });

    it('should display conversation links if tutorChatId and groupChannelId available and messaging enabled', () => {
        const tutorialGroup = createTutorialGroupDetailData({ groupChannelId: 2, tutorChatId: 3 });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(tutorChatLink).not.toBeNull();
        expect(tutorChatButton).toBeNull();
        expect(groupChannelLink).not.toBeNull();
    });

    it('should not display group channel link if groupChannelId not available and messaging enabled', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(groupChannelLink).toBeNull();
    });

    it('should display tutorial chat button if tutorChatId not available and messaging enabled', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        expect(tutorChatLink).toBeNull();
        expect(tutorChatButton).not.toBeNull();
    });

    it('should display current lesson button if currentTutorialLectureId available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const currentTutorialLectureLink = fixture.debugElement.query(By.css('[data-testid="tutorial-lecture-link"]'));
        expect(currentTutorialLectureLink).not.toBeNull();
    });

    it('should not display any management action if access level is STUDENT', () => {
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="registrations-link"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="new-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-session-button"]'))).toBeNull();
    });

    it('should display no other management actions than registrations link if access level is TUTOR_OF_OTHER_GROUP_OR_EDITOR_OR_INSTRUCTOR_OF_OTHER_COURSE', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.TUTOR_OF_OTHER_GROUP_OR_EDITOR_OR_INSTRUCTOR_OF_OTHER_COURSE);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="registrations-link"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="new-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-session-button"]'))).toBeNull();
    });

    it('should display no other management actions than registrations link and session actions if access level is TUTOR_OF_GROUP', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.TUTOR_OF_GROUP);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="registrations-link"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="new-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-session-button"]'))).not.toBeNull();
    });

    it('should display all management actions except delete group button if access level is EDITOR_OF_GROUP', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.EDITOR_OF_GROUP);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="registrations-link"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="new-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-session-button"]'))).not.toBeNull();
    });

    it('should display all management actions if access level is INSTRUCTOR_OF_GROUP_OR_ADMIN', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="registrations-link"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="new-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="edit-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="delete-session-button"]'))).not.toBeNull();
    });

    it('should display cancel session button but not activate session button if session is not cancelled', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
    });

    it('should display activate session button but not cancel session button if session is cancelled', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO({ isCancelled: true });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).not.toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).toBeNull();
    });

    it('should display neither activate nor cancel session button if session is cancelled by a free period', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO({ isCancelled: true, isCancelledByFreePeriod: true });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.css('[data-testid="activate-session-button"]'))).toBeNull();
        expect(fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]'))).toBeNull();
    });

    // tests validating conversion and exposure of input data

    it('should expose tutorChatLink if tutorChatId available', () => {
        const tutorialGroup = createTutorialGroupDetailData({ tutorChatId: 3 });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const tutorChatLink = component.tutorChatLink();
        expect(tutorChatLink).toBeDefined();
        expect(tutorChatLink!.routerLink).toEqual(['/courses', 1, 'communication']);
        expect(tutorChatLink!.queryParameters).toEqual({ conversationId: 3 });
    });

    it('should expose no tutorChatLink if tutorChatId is unavailable', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const tutorChatLink = component.tutorChatLink();
        expect(tutorChatLink).toBeUndefined();
    });

    it('should expose groupChannelLink if groupChannelId available', () => {
        const tutorialGroup = createTutorialGroupDetailData({ groupChannelId: 2 });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const groupChannelLink = component.groupChannelLink();
        expect(groupChannelLink).toBeDefined();
        expect(groupChannelLink!.routerLink).toEqual(['/courses', 1, 'communication']);
        expect(groupChannelLink!.queryParameters).toEqual({ conversationId: 2 });
    });

    it('should expose no groupChannelLink if groupChannelId is unavailable', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const groupChannelLink = component.groupChannelLink();
        expect(groupChannelLink).toBeUndefined();
    });

    it('should expose correct language', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupLanguage()).toBe(tutorialGroup.language);
    });

    it('should expose correct capacity', () => {
        const tutorialGroup = createTutorialGroupDetailData({ capacity: 10 });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCapacity()).toBe('10');
    });

    it('should expose placeholder if capacity not available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCapacity()).toBe('-');
    });

    it('should expose correct mode key if group is online', () => {
        const tutorialGroup = createTutorialGroupDetailData({ isOnline: true });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupMode()).toBe('artemisApp.generic.online');
    });

    it('should expose correct mode key if group is offline', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupMode()).toBe('artemisApp.generic.offline');
    });

    it('should expose correct campus', () => {
        const tutorialGroup = createTutorialGroupDetailData({ campus: 'Garching' });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCampus()).toBe(tutorialGroup.campus);
    });

    it('should expose placeholder if campus not available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCampus()).toBe('-');
    });

    it('should compute correct nextSession', () => {
        const nextSessionStart = dayjs('2025-01-15T13:00:00+01:00');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [firstSession, secondSession, thirdSession] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const nextSession = component.nextSession();
        expect(nextSession).toBeDefined();
        expect(nextSession!.id).toBe(2);
        expect(nextSession!.date.endsWith(nextSessionStart.format('DD.MM.YYYY'))).toBe(true);
        const expectedTime = `${nextSessionStart.format('HH:mm')}-${nextSessionEnd.format('HH:mm')}`;
        expect(nextSession!.time).toBe(expectedTime);
        expect(nextSession!.location).toBe('01.05.13');
        expect(nextSession!.isCancelled).toBe(false);
        expect(nextSession!.locationChanged).toBe(false);
        expect(nextSession!.timeChanged).toBe(false);
        expect(nextSession!.dateChanged).toBe(false);
        expect(nextSession!.attendance).toBeUndefined();
    });

    it('should expose no nextSession if no sessions available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();
        const nextSession = component.nextSession();
        expect(nextSession).toBeUndefined();
    });

    it('should expose no nextSession if there are no future sessions', () => {
        const referenceStart = dayjs().startOf('day').add(13, 'hour');
        const referenceEnd = referenceStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: referenceStart.subtract(3, 'week').toISOString(),
            end: referenceEnd.subtract(3, 'week').toISOString(),
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: referenceStart.subtract(2, 'week').toISOString(),
            end: referenceEnd.subtract(2, 'week').toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: referenceStart.subtract(1, 'week').toISOString(),
            end: referenceEnd.subtract(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [firstSession, secondSession, thirdSession] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const nextSession = component.nextSession();
        expect(nextSession).toBeUndefined();
    });

    it('should display no upcoming session disclaimer if no nextSession available', () => {
        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const noUpcomingEventDisclaimer = fixture.debugElement.query(By.css('[data-testid="no-upcoming-session-disclaimer"]'));
        expect(noUpcomingEventDisclaimer).not.toBeNull();
    });

    it('should compute correct averageAttendancePercentage', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 7,
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession, secondSession, thirdSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBe('Ø 70%');
    });

    it('should expose no averageAttendancePercentage if no attendances were recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession, secondSession, thirdSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBeUndefined();
    });

    it('should expose no averageAttendancePercentage if no capacity available for course', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 7,
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            attendanceCount: undefined,
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            attendanceCount: undefined,
        });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [firstSession, secondSession, thirdSession] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBeUndefined();
    });

    it('should compute correct pieChartData', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 7,
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession, secondSession, thirdSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(2);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('artemisApp.pages.tutorialGroupDetail.pieChartCategoryLabel.attended');
        expect(firstCategory.value).toBe(70);
        const secondCategory = pieChartData[1];
        expect(secondCategory.name).toBe('artemisApp.pages.tutorialGroupDetail.pieChartCategoryLabel.notAttended');
        expect(secondCategory.value).toBe(30);
    });

    it('should expose correct pieChartData if no capacity set for group', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 7,
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [firstSession, secondSession, thirdSession] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(1);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('artemisApp.pages.tutorialGroupDetail.pieChartCategoryLabel.notAttended');
        expect(firstCategory.value).toBe(100);
    });

    it('should expose correct pieChartData if no attendances recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
        });
        const secondSession = createRawTutorialGroupSessionDTO({
            id: 2,
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
        });
        const thirdSession = createRawTutorialGroupSessionDTO({
            id: 3,
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession, secondSession, thirdSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(1);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('artemisApp.pages.tutorialGroupDetail.pieChartCategoryLabel.notAttended');
        expect(firstCategory.value).toBe(100);
    });

    it('should compute green and gray color for average attendance < 70%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 6,
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--green)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 70% <= average attendance < 80%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 7,
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--yellow)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 80% <= average attendance < 90%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 8,
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--orange)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 90% <= average attendance', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 9,
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--red)', GraphColors.LIGHT_GREY]);
    });

    it('should compute gray color if no capacity set for group', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            attendanceCount: 9,
        });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [firstSession] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual([GraphColors.LIGHT_GREY]);
    });

    it('should compute gray color if no attendances recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession = createRawTutorialGroupSessionDTO({
            id: 1,
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
        });
        const tutorialGroup = createTutorialGroupDetailData({
            sessions: [firstSession],
            capacity: 10,
        });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual([GraphColors.LIGHT_GREY]);
    });

    it('should display no data available disclaimer if no average attendance available', () => {
        const tutorialGroup = createTutorialGroupDetailData({ campus: 'Garching' });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const noDataAvailableDisclaimer = fixture.debugElement.query(By.css('[data-testid="no-attendance-data-available-disclaimer"]'));
        expect(noDataAvailableDisclaimer).not.toBeNull();
    });

    // tests validating behavior triggered by controls

    it('should create tutor chat and navigate to it when tutor chat button is clicked', () => {
        const oneToOneChatService = TestBed.inject(OneToOneChatService);
        const createSpy = vi.spyOn(oneToOneChatService, 'create').mockReturnValue(of(new HttpResponse({ body: { id: 42 } as OneToOneChatDTO })));

        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        expect(tutorChatButton).not.toBeNull();

        tutorChatButton.triggerEventHandler('click');

        expect(createSpy).toHaveBeenCalledWith(1, 'gx89tum');
        expect(mockRouter.navigate).toHaveBeenCalledWith(['/courses', 1, 'communication'], { queryParams: { conversationId: 42 } });
    });

    it('should show error alert if tutor chat creation fails when tutor chat button is clicked', () => {
        const oneToOneChatService = TestBed.inject(OneToOneChatService);
        const alertService = TestBed.inject(AlertService);
        const createSpy = vi.spyOn(oneToOneChatService, 'create').mockReturnValue(throwError(() => new Error('network error')));
        const addErrorAlertSpy = vi.spyOn(alertService, 'addErrorAlert');

        const tutorialGroup = createTutorialGroupDetailData();
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        expect(tutorChatButton).not.toBeNull();

        tutorChatButton.triggerEventHandler('click');

        expect(createSpy).toHaveBeenCalledWith(1, 'gx89tum');
        expect(mockRouter.navigate).not.toHaveBeenCalled();
        expect(addErrorAlertSpy).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupDetail.networkError.createOneToOneChat');
    });

    it('should confirm tutorial group deletion and emit delete group event on accept', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirmSpy = vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation: { accept?: () => void }) => {
            confirmation.accept?.();
            return confirmationService;
        });
        const deleteGroupEmitSpy = vi.spyOn(component.onDeleteGroup, 'emit');

        fixture.debugElement.query(By.css('[data-testid="delete-button"]')).triggerEventHandler('click', { target: document.createElement('button') });

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(deleteGroupEmitSpy).toHaveBeenCalledWith({ courseId: 1, tutorialGroupId: 1 });
    });

    it('should confirm session deletion and emit delete session event on accept', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
        const confirmSpy = vi.spyOn(confirmationService, 'confirm').mockImplementation((confirmation: { accept?: () => void }) => {
            confirmation.accept?.();
            return confirmationService;
        });
        const deleteSessionEmitSpy = vi.spyOn(component.onDeleteSession, 'emit');

        fixture.debugElement.query(By.css('[data-testid="delete-session-button"]')).triggerEventHandler('click', { target: document.createElement('button') });

        expect(confirmSpy).toHaveBeenCalledOnce();
        expect(deleteSessionEmitSpy).toHaveBeenCalledWith({ courseId: 1, tutorialGroupId: 1, tutorialGroupSessionId: 1 });
    });

    it('should emit cancel session event when cancel session button is clicked', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const cancelSessionEmitSpy = vi.spyOn(component.onCancelSession, 'emit');

        fixture.debugElement.query(By.css('[data-testid="cancel-session-button"]')).triggerEventHandler('click');

        expect(cancelSessionEmitSpy).toHaveBeenCalledWith({ courseId: 1, tutorialGroupId: 1, tutorialGroupSessionId: 1 });
    });

    it('should emit activate session event when activate session button is clicked', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO({ isCancelled: true });
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const activateSessionEmitSpy = vi.spyOn(component.onActivateSession, 'emit');

        fixture.debugElement.query(By.css('[data-testid="activate-session-button"]')).triggerEventHandler('click');

        expect(activateSessionEmitSpy).toHaveBeenCalledWith({ courseId: 1, tutorialGroupId: 1, tutorialGroupSessionId: 1 });
    });

    it('should emit update session event after edit session flow', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const updateSessionEmitSpy = vi.spyOn(component.onUpdateSession, 'emit');
        const modal = fixture.debugElement.query(By.directive(TutorialSessionCreateOrEditModalComponent)).componentInstance as TutorialSessionCreateOrEditModalComponent;
        const updateTutorialGroupSessionData: UpdateTutorialGroupSessionData = {
            tutorialGroupSessionId: 1,
            updateTutorialGroupSessionRequest: {
                date: '2025-01-20',
                startTime: '10:00',
                endTime: '12:00',
                location: '02.03.04',
                attendance: 8,
            },
        };

        fixture.debugElement.query(By.css('[data-testid="edit-session-button"]')).triggerEventHandler('click');
        modal.onUpdate.emit(updateTutorialGroupSessionData);

        expect(updateSessionEmitSpy).toHaveBeenCalledWith({
            courseId: 1,
            tutorialGroupId: 1,
            tutorialGroupSessionId: 1,
            updateTutorialGroupSessionRequest: updateTutorialGroupSessionData.updateTutorialGroupSessionRequest,
        });
    });

    it('should emit create session event after new session flow', () => {
        fixture.componentRef.setInput('loggedInUserAccessLevel', TutorialGroupDetailAccessLevel.INSTRUCTOR_OF_GROUP_OR_ADMIN);
        const session = createRawTutorialGroupSessionDTO();
        const tutorialGroup = createTutorialGroupDetailData({ sessions: [session] });
        fixture.componentRef.setInput('tutorialGroup', tutorialGroup);
        fixture.detectChanges();

        const createSessionEmitSpy = vi.spyOn(component.onCreateSession, 'emit');
        const modal = fixture.debugElement.query(By.directive(TutorialSessionCreateOrEditModalComponent)).componentInstance as TutorialSessionCreateOrEditModalComponent;
        const createTutorialGroupSessionRequest: CreateOrUpdateTutorialGroupSessionRequest = {
            date: '2025-01-20',
            startTime: '10:00',
            endTime: '12:00',
            location: '02.03.04',
            attendance: 8,
        };

        fixture.debugElement.query(By.css('[data-testid="new-session-button"]')).triggerEventHandler('click');
        modal.onCreate.emit(createTutorialGroupSessionRequest);

        expect(createSessionEmitSpy).toHaveBeenCalledWith({
            courseId: 1,
            tutorialGroupId: 1,
            createTutorialGroupSessionRequest: createTutorialGroupSessionRequest,
        });
    });
});

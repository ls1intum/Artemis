import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseTutorialGroupDetailComponent } from './course-tutorial-group-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockDirective, MockService } from 'ng-mocks';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { AlertService } from 'app/shared/service/alert.service';
import { Router, RouterLink } from '@angular/router';
import { RawTutorialGroupDetailGroupDTO, TutorialGroupDetailGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { RawTutorialGroupDetailSessionDTO } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import * as CourseModel from 'app/core/course/shared/entities/course.model';

import { By } from '@angular/platform-browser';
import dayjs from 'dayjs/esm';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ScaleType } from '@swimlane/ngx-charts';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';

describe('NewTutorialGroupDetail', () => {
    let component: CourseTutorialGroupDetailComponent;
    let fixture: ComponentFixture<CourseTutorialGroupDetailComponent>;

    const mockTranslateService = new MockTranslateService();
    mockTranslateService.use('en');
    const mockAccountService = new MockAccountService();
    mockAccountService.userIdentity = new User(undefined, 'artemis_admin');

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseTutorialGroupDetailComponent],
            providers: [
                { provide: TranslateService, useValue: mockTranslateService },
                { provide: AccountService, useValue: mockAccountService },
                { provide: OneToOneChatService, useValue: MockService(OneToOneChatService) },
                { provide: AlertService, useValue: MockService(AlertService) },
                { provide: Router, useValue: MockService(Router) },
            ],
            declarations: [MockDirective(TranslateDirective), MockDirective(RouterLink)],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupDetailComponent);
        component = fixture.componentInstance;

        jest.spyOn(CourseModel, 'isMessagingEnabled').mockReturnValue(true);

        fixture.componentRef.setInput('course', { id: 1 } as Course);
    });

    // at top of the test file
    beforeAll(() => {
        process.env.TZ = 'Europe/Berlin'; // pin TZ
        jest.useFakeTimers();
        // pick a winter date (no DST jump). Any fixed date works, just be consistent.
        jest.setSystemTime(new Date('2025-01-14T10:00:00+01:00'));
    });

    afterAll(() => {
        jest.useRealTimers();
    });

    it('should display no conversation links if messaging disabled', () => {
        jest.spyOn(CourseModel, 'isMessagingEnabled').mockReturnValue(false);
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(tutorChatLink).toBeNull();
        expect(tutorChatButton).toBeNull();
        expect(groupChannelLink).toBeNull();
    });

    it('should display conversation links if tutorChatId and groupChannelId available and messaging enabled', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(tutorChatLink).not.toBeNull();
        expect(tutorChatButton).toBeNull();
        expect(groupChannelLink).not.toBeNull();
    });

    it('should not display group channel link if groupChannelId not available and messaging enabled', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: undefined,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        const groupChannelLink = fixture.debugElement.query(By.css('[data-testid="group-channel-link"]'));
        expect(groupChannelLink).toBeNull();
    });

    it('should display tutorial chat button if tutorChatId not available and messaging enabled', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: undefined,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        const tutorChatLink = fixture.debugElement.query(By.css('[data-testid="tutor-chat-link"]'));
        const tutorChatButton = fixture.debugElement.query(By.css('[data-testid="tutor-chat-button"]'));
        expect(tutorChatLink).toBeNull();
        expect(tutorChatButton).not.toBeNull();
    });

    it('should expose correct language', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupLanguage()).toBe(testTutorialGroup.language);
    });

    it('should expose correct capacity', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCapacity()).toBe('10');
    });

    it('should expose placeholder if capacity not available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCapacity()).toBe('-');
    });

    it('should expose correct mode key if group is online', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: true,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupMode()).toBe('artemisApp.generic.online');
    });

    it('should expose correct mode key if group is offline', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupMode()).toBe('artemisApp.generic.offline');
    });

    it('should expose correct campus', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCampus()).toBe(testTutorialGroup.campus);
    });

    it('should expose placeholder if campus not available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        expect(component.tutorialGroupCampus()).toBe('-');
    });

    it('should compute correct nextSession', () => {
        const nextSessionStart = dayjs('2025-01-15T13:00:00+01:00');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const nextSession = component.nextSession();
        expect(nextSession).toBeDefined();
        expect(nextSession!.date.endsWith(nextSessionStart.format('DD.MM.YYYY'))).toBeTrue();

        // compute the expected string from the same source to avoid TZ drift
        const expectedTime = `${nextSessionStart.format('HH:mm')}-${nextSessionEnd.format('HH:mm')}`;
        expect(nextSession!.time).toBe(expectedTime);

        expect(nextSession!.location).toBe('01.05.13');
        expect(nextSession!.isCancelled).toBeFalse();
        expect(nextSession!.locationChanged).toBeFalse();
        expect(nextSession!.timeChanged).toBeFalse();
        expect(nextSession!.dateChanged).toBeFalse();
        expect(nextSession!.attendance).toBeUndefined();
    });

    it('should expose no nextSession if no sessions available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();
        const nextSession = component.nextSession();
        expect(nextSession).toBeUndefined();
    });

    it('should expose no nextSession if there are no future sessions', () => {
        const referenceStart = dayjs().startOf('day').add(13, 'hour');
        const referenceEnd = referenceStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: referenceStart.subtract(3, 'week').toISOString(),
            end: referenceEnd.subtract(3, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: referenceStart.subtract(2, 'week').toISOString(),
            end: referenceEnd.subtract(2, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: referenceStart.subtract(1, 'week').toISOString(),
            end: referenceEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const nextSession = component.nextSession();
        expect(nextSession).toBeUndefined();
    });

    it('should compute correct averageAttendancePercentage', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBe('Ø 70%');
    });

    it('should expose no averageAttendancePercentage if no attendances were recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBeUndefined();
    });

    it('should expose no averageAttendancePercentage if no capacity available for course', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        expect(component.averageAttendancePercentage()).toBeUndefined();
    });

    it('should compute correct pieChartData', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(2);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('Attended');
        expect(firstCategory.value).toBe(70);
        const secondCategory = pieChartData[1];
        expect(secondCategory.name).toBe('Not Attended');
        expect(secondCategory.value).toBe(30);
    });

    it('should expose correct pieChartData if no capacity set for group', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(1);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('Not Attended');
        expect(firstCategory.value).toBe(100);
    });

    it('should expose correct pieChartData if no attendances recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const secondSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.toISOString(),
            end: nextSessionEnd.toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const thirdSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.add(1, 'week').toISOString(),
            end: nextSessionEnd.add(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession, secondSession, thirdSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartData = component.pieChartData();
        expect(pieChartData).toBeDefined();
        expect(pieChartData).toHaveLength(1);
        const firstCategory = pieChartData[0];
        expect(firstCategory.name).toBe('Not Attended');
        expect(firstCategory.value).toBe(100);
    });

    it('should compute green and gray color for average attendance < 70%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 6,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--green)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 70% <= average attendance < 80%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 7,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--yellow)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 80% <= average attendance < 90%', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 8,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--orange)', GraphColors.LIGHT_GREY]);
    });

    it('should compute green and gray color for 90% <= average attendance', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 9,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual(['var(--red)', GraphColors.LIGHT_GREY]);
    });

    it('should compute gray color if no capacity set for group', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: 9,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual([GraphColors.LIGHT_GREY]);
    });

    it('should compute gray color if no attendances recorded', () => {
        const nextSessionStart = dayjs().startOf('day').add(1, 'day').add(13, 'hour');
        const nextSessionEnd = nextSessionStart.add(2, 'hour');
        const firstSession: RawTutorialGroupDetailSessionDTO = {
            start: nextSessionStart.subtract(1, 'week').toISOString(),
            end: nextSessionEnd.subtract(1, 'week').toISOString(),
            location: '01.05.13',
            isCancelled: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [firstSession],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const pieChartColors = component.pieChartColors();
        expect(pieChartColors).toBeDefined();
        expect(pieChartColors.group).toBe(ScaleType.Ordinal);
        expect(pieChartColors.domain).toEqual([GraphColors.LIGHT_GREY]);
    });

    it('should expose tutorChatLink if tutorChatId available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const tutorChatLink = component.tutorChatLink();
        expect(tutorChatLink).toBeDefined();
        expect(tutorChatLink!.routerLink).toEqual(['/courses', 1, 'communication']);
        expect(tutorChatLink!.queryParameters).toEqual({ conversationId: 3 });
    });

    it('should expose no tutorChatLink if tutorChatId is unavailable', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: undefined,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const tutorChatLink = component.tutorChatLink();
        expect(tutorChatLink).toBeUndefined();
    });

    it('should expose groupChannelLink if groupChannelId available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const groupChannelLink = component.groupChannelLink();
        expect(groupChannelLink).toBeDefined();
        expect(groupChannelLink!.routerLink).toEqual(['/courses', 1, 'communication']);
        expect(groupChannelLink!.queryParameters).toEqual({ conversationId: 2 });
    });

    it('should expose no groupChannelLink if groupChannelId is unavailable', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: 10,
            campus: undefined,
            groupChannelId: undefined,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const groupChannelLink = component.groupChannelLink();
        expect(groupChannelLink).toBeUndefined();
    });

    it('should display no data available disclaimer if no average attendance available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const noDataAvailableDisclaimer = fixture.debugElement.query(By.css('[data-testid="no-attendance-data-available-disclaimer"]'));
        expect(noDataAvailableDisclaimer).not.toBeNull();
    });

    it('should display no upcoming session disclaimer if no nextSession available', () => {
        const raw: RawTutorialGroupDetailGroupDTO = {
            id: 1,
            title: 'TG 1 MN 13',
            language: 'English',
            isOnline: false,
            sessions: [],
            teachingAssistantName: 'Marlon Nienaber',
            teachingAssistantLogin: 'gx89tum',
            teachingAssistantImageUrl: undefined,
            capacity: undefined,
            campus: 'Garching',
            groupChannelId: 2,
            tutorChatId: 3,
        };
        const testTutorialGroup = new TutorialGroupDetailGroupDTO(raw);
        fixture.componentRef.setInput('tutorialGroup', testTutorialGroup);
        fixture.detectChanges();

        const noUpcomingEventDisclaimer = fixture.debugElement.query(By.css('[data-testid="no-upcoming-session-disclaimer"]'));
        expect(noUpcomingEventDisclaimer).not.toBeNull();
    });
});

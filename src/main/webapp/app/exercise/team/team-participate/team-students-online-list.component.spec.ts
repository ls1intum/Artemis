import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { captureExceptionMock } = vi.hoisted(() => ({
    captureExceptionMock: vi.fn(),
}));

vi.mock('@sentry/angular', () => ({
    captureException: captureExceptionMock,
}));

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { OnlineTeamStudent, Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamStudentsOnlineListComponent } from 'app/exercise/team/team-participate/team-students-online-list.component';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('TeamStudentsOnlineListComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TeamStudentsOnlineListComponent>;
    let component: TeamStudentsOnlineListComponent;
    let accountService: AccountService;
    let websocketService: MockWebsocketService;
    let typingSubject: Subject<void>;
    let currentUser: User;
    let currentUserTeammate: User;
    let alphabeticallyFirstTeammate: User;
    let participation: StudentParticipation;
    let identitySpy: ReturnType<typeof vi.spyOn>;
    let sendSpy: ReturnType<typeof vi.spyOn>;
    let subscribeSpy: ReturnType<typeof vi.spyOn>;

    const now = dayjs('2025-01-01T12:00:00.000Z');
    const participationId = 123;
    const websocketTopic = `/topic/participations/${participationId}/team`;

    const flushIdentity = async () => {
        await Promise.resolve();
    };

    const setInputsAndInitialize = async (withTyping$ = true) => {
        fixture = TestBed.createComponent(TeamStudentsOnlineListComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('participation', participation);
        if (withTyping$) {
            fixture.componentRef.setInput('typing$', typingSubject.asObservable());
        }
        fixture.detectChanges();
        await flushIdentity();
    };

    const recentTypingStudent = (): OnlineTeamStudent => ({
        login: alphabeticallyFirstTeammate.login!,
        lastTypingDate: now.subtract(500, 'milliseconds').toISOString() as unknown as dayjs.Dayjs,
        lastActionDate: now.subtract(5, 'seconds').toISOString() as unknown as dayjs.Dayjs,
    });

    const staleTypingStudent = (): OnlineTeamStudent => ({
        login: currentUserTeammate.login!,
        lastTypingDate: now.subtract(3, 'seconds').toISOString() as unknown as dayjs.Dayjs,
        lastActionDate: now.subtract(10, 'seconds').toISOString() as unknown as dayjs.Dayjs,
    });

    beforeEach(async () => {
        vi.setSystemTime(now.toDate());

        currentUser = { id: 1, login: 'current-user', name: 'Current User' } as User;
        currentUserTeammate = { id: 2, login: 'zoe', name: 'Zoe Zebra' } as User;
        alphabeticallyFirstTeammate = { id: 3, login: 'alice', name: 'Alice Alpha' } as User;
        participation = {
            id: participationId,
            team: {
                students: [currentUserTeammate, currentUser, alphabeticallyFirstTeammate],
            } as Team,
        } as StudentParticipation;
        typingSubject = new Subject<void>();

        await TestBed.configureTestingModule({
            imports: [TeamStudentsOnlineListComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        }).compileComponents();

        accountService = TestBed.inject(AccountService);
        websocketService = TestBed.inject(WebsocketService) as unknown as MockWebsocketService;
        identitySpy = vi.spyOn(accountService, 'identity').mockResolvedValue(currentUser);
        sendSpy = vi.spyOn(websocketService, 'send');
        subscribeSpy = vi.spyOn(websocketService, 'subscribe');
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
        captureExceptionMock.mockReset();
    });

    it('initializes from AccountService, subscribes to the team topic, and triggers a refresh after 700ms', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize();

        expect(identitySpy).toHaveBeenCalledOnce();
        expect(component.currentUser).toEqual(currentUser);
        expect(subscribeSpy).toHaveBeenCalledWith(websocketTopic);
        expect(component.websocketTopic).toBe(websocketTopic);
        expect(component.team).toBe(participation.team);
        expect(component.self).toEqual(currentUser);
        expect(component.otherStudents).toEqual([currentUserTeammate, alphabeticallyFirstTeammate]);
        expect(component.studentList).toEqual([currentUser, alphabeticallyFirstTeammate, currentUserTeammate]);
        expect(component.isSelf(currentUser)).toBe(true);
        expect(component.isOther(currentUserTeammate)).toBe(true);
        expect(sendSpy).not.toHaveBeenCalled();

        vi.advanceTimersByTime(699);
        expect(sendSpy).not.toHaveBeenCalled();

        vi.advanceTimersByTime(1);
        expect(sendSpy).toHaveBeenCalledOnce();
        expect(sendSpy).toHaveBeenCalledWith(`${websocketTopic}/trigger`, {});
    });

    it('receives online students, converts server timestamps, computes typing students, and removes expired typing indicators', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize();
        vi.advanceTimersByTime(700);
        sendSpy.mockClear();

        websocketService.emit(websocketTopic, [recentTypingStudent(), staleTypingStudent()]);

        expect(component.onlineTeamStudents).toHaveLength(2);
        expect(dayjs.isDayjs(component.onlineTeamStudents[0].lastTypingDate)).toBe(true);
        expect(dayjs.isDayjs(component.onlineTeamStudents[0].lastActionDate)).toBe(true);
        expect(component.typingTeamStudents.map((student) => student.login)).toEqual([alphabeticallyFirstTeammate.login]);
        expect(component.isOnline(alphabeticallyFirstTeammate)).toBe(true);
        expect(component.isOnline(currentUser)).toBe(false);
        expect(component.lastActionDate(alphabeticallyFirstTeammate)?.toISOString()).toBe(now.subtract(5, 'seconds').toISOString());
        expect(component.isTyping(alphabeticallyFirstTeammate)).toBe(true);
        expect(component.isTyping(currentUserTeammate)).toBe(false);

        vi.advanceTimersByTime(1501);

        expect(component.typingTeamStudents).toEqual([]);
        expect(component.isTyping(alphabeticallyFirstTeammate)).toBe(false);
    });

    it('keeps null timestamps from the server as null values', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize();

        websocketService.emit(websocketTopic, [
            {
                login: currentUserTeammate.login,
                lastTypingDate: null as unknown as dayjs.Dayjs,
                lastActionDate: null as unknown as dayjs.Dayjs,
            },
        ]);

        expect(component.onlineTeamStudents).toEqual([
            expect.objectContaining({
                login: currentUserTeammate.login,
                lastTypingDate: null,
                lastActionDate: null,
            }),
        ]);
        expect(component.typingTeamStudents).toEqual([]);
        expect(component.lastActionDate(currentUserTeammate)).toBeNull();
        expect(component.isTyping(currentUserTeammate)).toBe(false);
    });

    it('sends typing updates immediately and throttles repeated sends until the interval passes', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize();
        vi.advanceTimersByTime(700);
        sendSpy.mockClear();

        typingSubject.next();
        typingSubject.next();

        expect(sendSpy).toHaveBeenCalledOnce();
        expect(sendSpy).toHaveBeenCalledWith(`${websocketTopic}/typing`, {});

        vi.advanceTimersByTime(Math.ceil(component.SEND_TYPING_INTERVAL) + 1);
        typingSubject.next();

        expect(sendSpy).toHaveBeenCalledTimes(2);
        expect(sendSpy).toHaveBeenLastCalledWith(`${websocketTopic}/typing`, {});
    });

    it('reports websocket receiver and typing stream errors to Sentry', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize();

        const receiverError = new Error('receiver failed');
        const typingError = new Error('typing failed');
        const onlineStudentsChannel = (websocketService as unknown as { channels: Map<string, Subject<OnlineTeamStudent[]>> }).channels.get(websocketTopic)!;

        onlineStudentsChannel.error(receiverError);
        typingSubject.error(typingError);

        expect(captureExceptionMock).toHaveBeenCalledTimes(2);
        expect(captureExceptionMock).toHaveBeenNthCalledWith(1, receiverError);
        expect(captureExceptionMock).toHaveBeenNthCalledWith(2, typingError);
    });

    it('ignores later websocket updates after destroy', async () => {
        vi.useFakeTimers();

        await setInputsAndInitialize(false);

        const initialOnlineStudent = recentTypingStudent();
        websocketService.emit(websocketTopic, [initialOnlineStudent]);
        expect(component.onlineTeamStudents).toEqual([expect.objectContaining({ login: initialOnlineStudent.login })]);

        component.ngOnDestroy();
        websocketService.emit(websocketTopic, [staleTypingStudent()]);

        expect(component.onlineTeamStudents).toEqual([expect.objectContaining({ login: initialOnlineStudent.login })]);
    });
});

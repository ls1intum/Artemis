import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TeamService } from './team.service';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Course } from 'app/course/shared/entities/course.model';

import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import dayjs from 'dayjs/esm';
import { Subject } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { ConnectionState } from 'app/foundation/service/websocket.service';
import { Team, TeamAssignmentPayload, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { TeamSearchUser } from 'app/exercise/shared/entities/team/team-search-user.model';
import * as DownloadUtil from 'app/foundation/util/download.util';

describe('Team overview service', () => {
    let service: TeamService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(AccountService), MockProvider(WebsocketService)] });
        service = TestBed.inject(TeamService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        http.verify();
        vi.restoreAllMocks();
    });

    it.each(['student', 'staff'] as const)('should request the explicit %s overview and preserve its payload', (purpose) => {
        const setAccessRights = vi.spyOn(TestBed.inject(AccountService), 'setAccessRightsForCourse');
        let received: Course | undefined;
        service
            .findCourseWithExercisesAndParticipationsForTeam({ id: 7, isAtLeastInstructor: true }, { shortName: 'team & one' }, purpose)
            .subscribe((response) => (received = response.body ?? undefined));
        const request = http.expectOne((r) => r.method === 'GET');
        request.flush({
            id: 7,
            exercises: [
                {
                    id: 8,
                    type: 'programming',
                    releaseDate: '2026-01-01T10:00:00Z',
                    teams: [{ id: 10, students: [{ id: 3, login: 'alice' }] }],
                    studentParticipations: [
                        {
                            id: 9,
                            type: 'programming',
                            repositoryUri: 'https://example.org/team.git',
                            submissionCount: 2,
                            submissions: [{ id: 11, submissionExerciseType: 'programming', commitHash: 'abcdef', results: [{ id: 12, score: 90 }] }],
                        },
                    ],
                },
            ],
        });
        expect(request.request.url).toBe(`/api/exercise/courses/7/teams/team%20%26%20one/with-exercises-and-participations${purpose === 'staff' ? '/for-staff' : ''}`);
        expect(received).toBeInstanceOf(Course);
        expect(setAccessRights).toHaveBeenCalledExactlyOnceWith(received);
        const exercise = received?.exercises?.[0];
        expect(exercise).toBeInstanceOf(ProgrammingExercise);
        expect(dayjs.isDayjs(exercise?.releaseDate)).toBe(true);
        expect(exercise?.teams?.[0]).toBeInstanceOf(Team);
        expect(exercise?.teams?.[0].students?.[0]).toBeInstanceOf(User);
        const participation = exercise?.studentParticipations?.[0];
        expect(participation).toBeInstanceOf(ProgrammingExerciseStudentParticipation);
        expect(participation?.submissionCount).toBe(2);
        const submission = participation?.submissions?.[0];
        expect(submission).toBeInstanceOf(ProgrammingSubmission);
        expect(submission).toMatchObject({ commitHash: 'abcdef' });
        expect(submission?.results?.[0]).toBeInstanceOf(Result);
        expect(submission?.results?.[0].score).toBe(90);
    });

    it('keeps missing overview collections optional', () => {
        service.findCourseWithExercisesAndParticipationsForTeam({ id: 7 }, { shortName: 'team' }).subscribe(({ body }) => {
            expect(body).toBeInstanceOf(Course);
            expect(body?.exercises).toBeUndefined();
        });
        http.expectOne('/api/exercise/courses/7/teams/team/with-exercises-and-participations').flush({ id: 7 });
    });
    it.each(['create', 'update'] as const)('sends only student and owner ids when teams %s', (method) => {
        const team: Team = {
            id: 9,
            name: 'Pair',
            shortName: 'pair',
            students: [
                { id: 3, login: 'alice', internal: true },
                { login: 'unresolved', internal: true },
            ],
            owner: { id: 4, login: 'tutor', internal: true },
        };
        let result: Team | undefined;
        service[method](Object.assign(new TextExercise(undefined, undefined), { id: 7 }), team).subscribe((response) => (result = response.body ?? undefined));
        const req = http.expectOne({ method: method === 'create' ? 'POST' : 'PUT', url: `/api/exercise/exercises/7/teams${method === 'update' ? '/9' : ''}` });
        expect(req.request.body).toEqual({ id: 9, name: 'Pair', shortName: 'pair', students: [3], ownerId: 4 });
        req.flush({ id: 9, name: 'Pair', createdDate: '2026-01-01T10:00:00Z', lastModifiedDate: '2026-01-02T10:00:00Z' });
        expect(result?.name).toBe('Pair');
        expect(result?.createdDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
        expect(result?.lastModifiedDate?.toISOString()).toBe('2026-01-02T10:00:00.000Z');
        expect(team.students?.[0].login).toBe('alice');
    });

    it('loads a single team with converted dates', () => {
        service.find(Object.assign(new TextExercise(undefined, undefined), { id: 7 }), 9).subscribe((response) => {
            expect(response.body?.id).toBe(9);
            expect(dayjs.isDayjs(response.body?.createdDate)).toBe(true);
            expect(dayjs.isDayjs(response.body?.lastModifiedDate)).toBe(true);
        });
        http.expectOne({ method: 'GET', url: '/api/exercise/exercises/7/teams/9' }).flush({ id: 9, createdDate: '2026-01-01T10:00:00Z', lastModifiedDate: '2026-01-02T10:00:00Z' });
    });

    it.each([undefined, 0, 4])('loads teams with optional owner filter %s and converts every date', (ownerId) => {
        service.findAllByExerciseId(7, ownerId).subscribe((response) => {
            expect(response.body?.map((team) => team.id)).toEqual([9, 10]);
            expect(response.body?.every((team) => dayjs.isDayjs(team.createdDate) && dayjs.isDayjs(team.lastModifiedDate))).toBe(true);
        });
        const req = http.expectOne((r) => r.url.split('?')[0] === '/api/exercise/exercises/7/teams');
        expect(req.request.method).toBe('GET');
        expect(new URLSearchParams(req.request.urlWithParams.split('?')[1]).get('teamOwnerId')).toBe(ownerId ? '4' : null);
        req.flush([9, 10].map((id) => ({ id, createdDate: '2026-01-01T10:00:00Z', lastModifiedDate: '2026-01-02T10:00:00Z' })));
    });

    it('deletes a selected team and checks short name and course-scoped member search', () => {
        service.delete(Object.assign(new TextExercise(undefined, undefined), { id: 7 }), 9).subscribe(({ body, status }) => {
            expect(body).toBeNull();
            expect(status).toBe(200);
        });
        http.expectOne({ method: 'DELETE', url: '/api/exercise/exercises/7/teams/9' }).flush(null, { status: 200, statusText: 'OK' });
        service.existsByShortName({ id: 2 }, 'pair & one').subscribe((response) => expect(response.body).toBe(true));
        const exists = http.expectOne((req) => req.method === 'GET' && req.url.split('?')[0] === '/api/exercise/courses/2/teams/exists');
        expect(new URLSearchParams(exists.request.urlWithParams.split('?')[1]).get('shortName')).toBe('pair & one');
        exists.flush(true);
        service.searchInCourseForExerciseTeam({ id: 2 }, Object.assign(new TextExercise(undefined, undefined), { id: 7 }), 'Alice & Bob').subscribe(({ body }) => {
            expect(body?.[0]).toBeInstanceOf(TeamSearchUser);
            expect(body?.[0].login).toBe('alice');
        });
        const search = http.expectOne((req) => req.method === 'GET' && req.url.split('?')[0] === '/api/exercise/courses/2/exercises/7/team-search-users');
        expect(new URLSearchParams(search.request.urlWithParams.split('?')[1]).get('loginOrName')).toBe('Alice & Bob');
        search.flush([{ login: 'alice' }]);
    });

    it('imports list members by login or registration number without shipping private user data', () => {
        service
            .importTeams(
                Object.assign(new TextExercise(undefined, undefined), { id: 7 }),
                [
                    {
                        name: 'Pair',
                        shortName: 'pair',
                        students: [
                            { id: 3, login: 'alice', email: 'private@example.org', internal: true },
                            { visibleRegistrationNumber: '123', internal: true },
                        ],
                    },
                ],
                TeamImportStrategyType.CREATE_ONLY,
            )
            .subscribe(({ body }) => {
                expect(body?.[0]).toBeInstanceOf(Team);
                expect(body?.[0].id).toBe(9);
                expect(body?.[0].createdDate?.toISOString()).toBe('2026-01-01T10:00:00.000Z');
            });
        const req = http.expectOne({ method: 'PUT', url: '/api/exercise/exercises/7/teams/import-from-list?importStrategyType=CREATE_ONLY' });
        expect(req.request.body).toEqual([{ name: 'Pair', shortName: 'pair', students: [{ login: 'alice' }, { visibleRegistrationNumber: '123' }] }]);
        req.flush([{ id: 9, createdDate: '2026-01-01T10:00:00Z' }]);
        service
            .importTeamsFromSourceExercise(
                Object.assign(new TextExercise(undefined, undefined), { id: 7 }),
                Object.assign(new TextExercise(undefined, undefined), { id: 8 }),
                TeamImportStrategyType.PURGE_EXISTING,
            )
            .subscribe((response) => expect(response.body).toEqual([{ id: 10 }]));
        const source = http.expectOne((req) => req.method === 'PUT' && req.url.split('?')[0] === '/api/exercise/exercises/7/teams/import-from-exercise');
        const query = new URLSearchParams(source.request.urlWithParams.split('?')[1]);
        expect(query.get('sourceExerciseId')).toBe('8');
        expect(query.get('importStrategyType')).toBe('PURGE_EXISTING');
        expect(source.request.body).toBeNull();
        source.flush([{ id: 10 }]);
    });

    it('exports a downloadable team roster retaining optional public fields', async () => {
        const download = vi.spyOn(DownloadUtil, 'downloadFile').mockImplementation(() => {});
        service.exportTeams([
            { name: 'Pair', students: [{ login: 'alice', firstName: 'Alice', lastName: 'A', visibleRegistrationNumber: '123', internal: true }, { internal: true }] },
            {},
        ]);
        expect(download).toHaveBeenCalledOnce();
        const [blob, filename] = download.mock.calls[0];
        expect(filename).toBe('teams.json');
        expect(blob.type).toBe('application/json');
        const text = await new Promise<string>((resolve) => {
            const reader = new FileReader();
            reader.onload = () => resolve(String(reader.result));
            reader.readAsText(blob);
        });
        expect(JSON.parse(text)).toEqual([
            { teamName: 'Pair', username: 'alice', firstName: 'Alice', lastName: 'A', registrationNumber: '123' },
            { teamName: 'Pair', username: '' },
        ]);
    });

    it('shares authenticated team assignment updates and tears down its connection listener', async () => {
        vi.useFakeTimers();
        try {
            const authentication = new Subject<User | undefined>();
            const connection = new Subject<ConnectionState>();
            const assignments = new Subject<TeamAssignmentPayload>();
            vi.spyOn(TestBed.inject(AccountService), 'getAuthenticationState').mockReturnValue(authentication);
            const websocket = TestBed.inject(WebsocketService);
            vi.spyOn(websocket, 'connectionState', 'get').mockReturnValue(connection);
            const subscribe = vi.spyOn(websocket, 'subscribe').mockReturnValue(assignments);
            const streamPromise = service.teamAssignmentUpdates;
            authentication.next({ id: 3, internal: true });
            await vi.advanceTimersByTimeAsync(500);
            connection.next(new ConnectionState(true, false));
            const stream = await streamPromise;
            expect(await service.teamAssignmentUpdates).toBe(stream);
            const received: TeamAssignmentPayload[] = [];
            const subscription = stream.subscribe((value) => received.push(value));
            const payload = { exerciseId: 7, teamId: 9, studentParticipations: [{ id: 11 }] };
            assignments.next(payload);
            expect(received).toEqual([payload]);
            expect(subscribe).toHaveBeenCalledExactlyOnceWith('/user/topic/team-assignments');
            service.ngOnDestroy();
            expect(connection.observed).toBe(false);
            authentication.next(undefined);
            await vi.advanceTimersByTimeAsync(500);
            expect(authentication.observed).toBe(false);
            subscription.unsubscribe();
        } finally {
            vi.useRealTimers();
        }
    });
});

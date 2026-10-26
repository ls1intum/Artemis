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
        let received: Course | undefined;
        service
            .findCourseWithExercisesAndParticipationsForTeam({ id: 7, isAtLeastInstructor: true }, { shortName: 'team' }, purpose)
            .subscribe((response) => (received = response.body ?? undefined));
        const request = http.expectOne((r) => r.method === 'GET');
        request.flush({ id: 7, exercises: [{ id: 8, studentParticipations: [{ id: 9, submissionCount: 2 }] }] });
        expect(request.request.url).toBe(`api/exercise/courses/7/teams/team/with-exercises-and-participations${purpose === 'staff' ? '/for-staff' : ''}`);
        expect(received?.exercises?.[0].studentParticipations?.[0].submissionCount).toBe(2);
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
        const req = http.expectOne({ method: method === 'create' ? 'POST' : 'PUT', url: `api/exercise/exercises/7/teams${method === 'update' ? '/9' : ''}` });
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
        http.expectOne({ method: 'GET', url: 'api/exercise/exercises/7/teams/9' }).flush({ id: 9, createdDate: '2026-01-01T10:00:00Z', lastModifiedDate: '2026-01-02T10:00:00Z' });
    });

    it.each([undefined, 4])('loads teams with optional owner filter %s and converts every date', (ownerId) => {
        service.findAllByExerciseId(7, ownerId).subscribe((response) => {
            expect(response.body?.map((team) => team.id)).toEqual([9, 10]);
            expect(response.body?.every((team) => dayjs.isDayjs(team.createdDate) && dayjs.isDayjs(team.lastModifiedDate))).toBe(true);
        });
        const req = http.expectOne((r) => r.url === 'api/exercise/exercises/7/teams');
        expect(req.request.method).toBe('GET');
        expect(req.request.params.get('teamOwnerId')).toBe(ownerId === undefined ? null : '4');
        req.flush([9, 10].map((id) => ({ id, createdDate: '2026-01-01T10:00:00Z', lastModifiedDate: '2026-01-02T10:00:00Z' })));
    });

    it('deletes a selected team and checks short name and course-scoped member search', () => {
        let deleted = false;
        service.delete(Object.assign(new TextExercise(undefined, undefined), { id: 7 }), 9).subscribe(() => (deleted = true));
        http.expectOne({ method: 'DELETE', url: 'api/exercise/exercises/7/teams/9' }).flush(null);
        expect(deleted).toBe(true);
        service.existsByShortName({ id: 2 }, 'pair').subscribe((response) => expect(response.body).toBe(true));
        http.expectOne({ method: 'GET', url: 'api/exercise/courses/2/teams/exists?shortName=pair' }).flush(true);
        service
            .searchInCourseForExerciseTeam({ id: 2 }, Object.assign(new TextExercise(undefined, undefined), { id: 7 }), 'alice')
            .subscribe((response) => expect(response.body?.[0].login).toBe('alice'));
        http.expectOne({ method: 'GET', url: 'api/exercise/courses/2/exercises/7/team-search-users?loginOrName=alice' }).flush([{ login: 'alice' }]);
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
            .subscribe((response) => expect(response.body?.[0].id).toBe(9));
        const req = http.expectOne({ method: 'PUT', url: 'api/exercise/exercises/7/teams/import-from-list?importStrategyType=CREATE_ONLY' });
        expect(req.request.body).toEqual([{ name: 'Pair', shortName: 'pair', students: [{ login: 'alice' }, { visibleRegistrationNumber: '123' }] }]);
        req.flush([{ id: 9 }]);
        service
            .importTeamsFromSourceExercise(
                Object.assign(new TextExercise(undefined, undefined), { id: 7 }),
                Object.assign(new TextExercise(undefined, undefined), { id: 8 }),
                TeamImportStrategyType.PURGE_EXISTING,
            )
            .subscribe((response) => expect(response.body).toEqual([{ id: 10 }]));
        const source = http.expectOne({ method: 'PUT', url: 'api/exercise/exercises/7/teams/import-from-exercise?sourceExerciseId=8&importStrategyType=PURGE_EXISTING' });
        expect(source.request.body).toEqual({});
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

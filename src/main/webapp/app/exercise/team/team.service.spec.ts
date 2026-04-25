import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TeamService } from './team.service';
import { Team, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import * as downloadUtil from 'app/shared/util/download.util';

describe('TeamService', () => {
    let service: TeamService;
    let httpMock: HttpTestingController;

    const exercise: Exercise = { id: 1 } as Exercise;

    beforeAll(() => {
        try {
            TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
        } catch {
            // Already initialized
        }
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(AccountService), MockProvider(WebsocketService)],
        });
        service = TestBed.inject(TeamService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a team and convert to input DTO', () => {
        const team: Team = {
            id: undefined,
            name: 'Team Alpha',
            shortName: 'alpha',
            image: 'alpha.png',
            students: [{ id: 10, login: 'student1' } as User, { id: 20, login: 'student2' } as User],
            owner: { id: 5 } as User,
        };

        service.create(exercise, team).subscribe((resp) => {
            expect(resp.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'POST', url: 'api/exercise/exercises/1/teams' });
        expect(req.request.body).toEqual({
            id: undefined,
            name: 'Team Alpha',
            shortName: 'alpha',
            image: 'alpha.png',
            students: [10, 20],
            ownerId: 5,
        });
        req.flush({ id: 1, name: 'Team Alpha', shortName: 'alpha' });
    });

    it('should update a team and convert to input DTO', () => {
        const team: Team = {
            id: 1,
            name: 'Team Beta',
            shortName: 'beta',
            students: [{ id: 30 } as User],
            owner: { id: 7 } as User,
        };

        service.update(exercise, team).subscribe((resp) => {
            expect(resp.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'PUT', url: 'api/exercise/exercises/1/teams/1' });
        expect(req.request.body).toEqual({
            id: 1,
            name: 'Team Beta',
            shortName: 'beta',
            image: undefined,
            students: [30],
            ownerId: 7,
        });
        req.flush({ id: 1, name: 'Team Beta', shortName: 'beta' });
    });

    it('should handle team with no students or owner', () => {
        const team: Team = {
            name: 'Empty Team',
            shortName: 'empty',
        };

        service.create(exercise, team).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.body).toEqual({
            id: undefined,
            name: 'Empty Team',
            shortName: 'empty',
            image: undefined,
            students: [],
            ownerId: undefined,
        });
        req.flush({ id: 2, name: 'Empty Team', shortName: 'empty' });
    });

    it('should import teams and convert to import DTOs', () => {
        const teams: Team[] = [
            {
                name: 'Team Import',
                shortName: 'imp',
                image: 'imp.png',
                students: [{ login: 'user1', visibleRegistrationNumber: '12345' } as User, { login: 'user2', visibleRegistrationNumber: '67890' } as User],
            },
        ];

        service.importTeams(exercise, teams, TeamImportStrategyType.CREATE_ONLY).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain('import-from-list');
        expect(req.request.url).toContain('importStrategyType=CREATE_ONLY');
        expect(req.request.body).toEqual([
            {
                name: 'Team Import',
                shortName: 'imp',
                image: 'imp.png',
                students: [
                    { login: 'user1', visibleRegistrationNumber: '12345' },
                    { login: 'user2', visibleRegistrationNumber: '67890' },
                ],
            },
        ]);
        req.flush([]);
    });

    it('should import teams with empty students', () => {
        const teams: Team[] = [{ name: 'No Students', shortName: 'ns' }];

        service.importTeams(exercise, teams, TeamImportStrategyType.PURGE_EXISTING).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.body).toEqual([
            {
                name: 'No Students',
                shortName: 'ns',
                image: undefined,
                students: [],
            },
        ]);
        req.flush([]);
    });

    it('should find a team', () => {
        service.find(exercise, 42).subscribe((resp) => {
            expect(resp.body?.id).toBe(42);
        });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/exercise/exercises/1/teams/42' });
        req.flush({ id: 42, name: 'Found Team' });
    });

    it('should find all teams by exercise id', () => {
        service.findAllByExerciseId(1).subscribe((resp) => {
            expect(resp.body).toHaveLength(2);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/exercise/exercises/1/teams');
        req.flush([{ id: 1 }, { id: 2 }]);
    });

    it('should find all teams by exercise id with team owner filter', () => {
        service.findAllByExerciseId(1, 42).subscribe((resp) => {
            expect(resp.body).toHaveLength(1);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/exercise/exercises/1/teams');
        expect(req.request.params.get('teamOwnerId')).toBe('42');
        req.flush([{ id: 1 }]);
    });

    it('should delete a team', () => {
        service.delete(exercise, 1).subscribe((resp) => {
            expect(resp.status).toBe(200);
        });

        const req = httpMock.expectOne({ method: 'DELETE', url: 'api/exercise/exercises/1/teams/1' });
        req.flush(null);
    });

    it('should check if team short name exists', () => {
        const course = { id: 10 } as any;
        service.existsByShortName(course, 'alpha').subscribe((resp) => {
            expect(resp.body).toBeTrue();
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toContain('teams/exists');
        req.flush(true);
    });

    it('should search for users in course', () => {
        const course = { id: 10 } as any;
        service.searchInCourseForExerciseTeam(course, exercise, 'john').subscribe((resp) => {
            expect(resp.body).toHaveLength(1);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toContain('team-search-users');
        req.flush([{ login: 'john' }]);
    });

    it('should import teams from source exercise', () => {
        const sourceExercise = { id: 99 } as Exercise;
        service.importTeamsFromSourceExercise(exercise, sourceExercise, TeamImportStrategyType.CREATE_ONLY).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain('import-from-exercise/99');
        req.flush([]);
    });

    it('should find course with exercises and participations for team', () => {
        const course = { id: 10 } as Course;
        const team: Team = { shortName: 'alpha' };

        service.findCourseWithExercisesAndParticipationsForTeam(course, team).subscribe((resp) => {
            expect(resp.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'GET', url: 'api/exercise/courses/10/teams/alpha/with-exercises-and-participations' });
        req.flush({ id: 10, title: 'Test Course' });
    });

    it('should export teams to JSON file', () => {
        const downloadSpy = jest.spyOn(downloadUtil, 'downloadFile').mockImplementation();

        const teams: Team[] = [
            {
                name: 'Team A',
                students: [{ login: 'student1', firstName: 'John', lastName: 'Doe', visibleRegistrationNumber: '12345' } as User, { login: 'student2' } as User],
            },
            {
                name: 'Team B',
                students: undefined,
            },
        ];

        service.exportTeams(teams);

        expect(downloadSpy).toHaveBeenCalledWith(expect.any(Blob), 'teams.json');
        downloadSpy.mockRestore();
    });

    it('should export teams with empty student list', () => {
        const downloadSpy = jest.spyOn(downloadUtil, 'downloadFile').mockImplementation();

        const teams: Team[] = [{ name: 'Empty', students: [] }];
        service.exportTeams(teams);

        expect(downloadSpy).toHaveBeenCalledWith(expect.any(Blob), 'teams.json');
        downloadSpy.mockRestore();
    });
});

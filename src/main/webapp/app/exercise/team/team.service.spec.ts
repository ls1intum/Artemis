import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TeamService } from 'app/exercise/team/team.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Team, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('TeamService', () => {
    let service: TeamService;
    let httpMock: HttpTestingController;

    const exercise: Exercise = { id: 1, type: 'programming' } as Exercise;

    const team: Team = {
        id: 10,
        name: 'Team Alpha',
        shortName: 'alpha',
        image: 'img.png',
        students: [{ id: 100, login: 'student1', visibleRegistrationNumber: '12345' } as any, { id: 101, login: 'student2', visibleRegistrationNumber: '67890' } as any],
        owner: { id: 200 } as any,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                TeamService,
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });
        service = TestBed.inject(TeamService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a team with correct DTO format', () => {
        service.create(exercise, team).subscribe((resp) => {
            expect(resp.body).toBeTruthy();
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercise/exercises/1/teams` });
        const sentBody = req.request.body;
        expect(sentBody.id).toBe(10);
        expect(sentBody.name).toBe('Team Alpha');
        expect(sentBody.shortName).toBe('alpha');
        expect(sentBody.students).toEqual([100, 101]);
        expect(sentBody.ownerId).toBe(200);
        req.flush(team);
    });

    it('should update a team with correct DTO format', () => {
        service.update(exercise, team).subscribe((resp) => {
            expect(resp.body).toBeTruthy();
        });

        const req = httpMock.expectOne({ method: 'PUT', url: `api/exercise/exercises/1/teams/10` });
        const sentBody = req.request.body;
        expect(sentBody.id).toBe(10);
        expect(sentBody.name).toBe('Team Alpha');
        expect(sentBody.shortName).toBe('alpha');
        expect(sentBody.students).toEqual([100, 101]);
        expect(sentBody.ownerId).toBe(200);
        req.flush(team);
    });

    it('should handle team with no students in create', () => {
        const emptyTeam: Team = { id: 11, name: 'Empty', shortName: 'empty' };
        service.create(exercise, emptyTeam).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        const sentBody = req.request.body;
        expect(sentBody.students).toEqual([]);
        expect(sentBody.name).toBe('Empty');
        expect(sentBody.ownerId).toBeUndefined();
        req.flush({});
    });

    it('should import teams with correct import DTO format', () => {
        const teams: Team[] = [team];
        service.importTeams(exercise, teams, TeamImportStrategyType.CREATE_ONLY).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain('import-from-list');
        const sentBody = req.request.body;
        expect(sentBody).toHaveLength(1);
        expect(sentBody[0].name).toBe('Team Alpha');
        expect(sentBody[0].shortName).toBe('alpha');
        expect(sentBody[0].students).toHaveLength(2);
        expect(sentBody[0].students[0].login).toBe('student1');
        expect(sentBody[0].students[0].visibleRegistrationNumber).toBe('12345');
        req.flush([]);
    });

    it('should find a team', () => {
        service.find(exercise, 10).subscribe((resp) => {
            expect(resp.body).toBeTruthy();
        });

        const req = httpMock.expectOne({ method: 'GET', url: `api/exercise/exercises/1/teams/10` });
        req.flush(team);
    });

    it('should find all teams by exercise id', () => {
        service.findAllByExerciseId(1).subscribe((resp) => {
            expect(resp.body).toHaveLength(1);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe('api/exercise/exercises/1/teams');
        req.flush([team]);
    });

    it('should delete a team', () => {
        service.delete(exercise, 10).subscribe((resp) => {
            expect(resp.status).toBe(200);
        });

        const req = httpMock.expectOne({ method: 'DELETE', url: `api/exercise/exercises/1/teams/10` });
        req.flush(null);
    });

    it('should check if team short name exists', () => {
        const course: Course = { id: 5 } as Course;
        service.existsByShortName(course, 'alpha').subscribe((resp) => {
            expect(resp.body).toBeTrue();
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toContain('exists');
        req.flush(true);
    });

    it('should search in course for exercise team', () => {
        const course: Course = { id: 5 } as Course;
        service.searchInCourseForExerciseTeam(course, exercise, 'student1').subscribe((resp) => {
            expect(resp.body).toBeTruthy();
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toContain('team-search-users');
        req.flush([]);
    });

    it('should import teams from source exercise', () => {
        const sourceExercise: Exercise = { id: 2 } as Exercise;
        service.importTeamsFromSourceExercise(exercise, sourceExercise, TeamImportStrategyType.CREATE_ONLY).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain('import-from-exercise/2');
        req.flush([]);
    });

    it('should export teams to JSON', () => {
        // Mock URL.createObjectURL/revokeObjectURL for jsdom
        const originalCreateObjectURL = URL.createObjectURL;
        const originalRevokeObjectURL = URL.revokeObjectURL;
        URL.createObjectURL = jest.fn().mockReturnValue('blob:url');
        URL.revokeObjectURL = jest.fn();

        const teams: Team[] = [
            {
                id: 1,
                name: 'Team A',
                shortName: 'a',
                students: [{ id: 1, login: 'user1', firstName: 'First', lastName: 'Last', visibleRegistrationNumber: '111' } as any, { id: 2, login: 'user2' } as any],
            },
        ];

        service.exportTeams(teams);

        expect(URL.createObjectURL).toHaveBeenCalled();

        URL.createObjectURL = originalCreateObjectURL;
        URL.revokeObjectURL = originalRevokeObjectURL;
    });

    it('should handle team with undefined name and shortName', () => {
        const minTeam: Team = { id: 12 };
        service.create(exercise, minTeam).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        const sentBody = req.request.body;
        expect(sentBody.name).toBe('');
        expect(sentBody.shortName).toBe('');
        expect(sentBody.students).toEqual([]);
        req.flush({});
    });

    it('should generate correct resource URL', () => {
        expect(TeamService.resourceUrl(42)).toBe('api/exercise/exercises/42/teams');
    });
});

import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TeamImportDTO, TeamInputDTO, TeamService } from './team.service';
import { Team, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

describe('Team Service', () => {
    let service: TeamService;
    let httpMock: HttpTestingController;

    const exercise = { id: 1 } as Exercise;
    const course = { id: 10 } as Course;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        });

        service = TestBed.inject(TeamService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a team and send TeamInputDTO', fakeAsync(() => {
        const team: Team = {
            id: undefined,
            name: 'Team Alpha',
            shortName: 'alpha',
            image: 'img.png',
            students: [{ id: 101, login: 'student1' } as User, { id: 102, login: 'student2' } as User],
            owner: { id: 201 } as User,
        };

        const expectedTeam: Team = { ...team, id: 1 };

        service.create(exercise, team).subscribe((res) => {
            expect(res.body).toBeDefined();
            expect(res.body!.id).toBe(1);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercise/exercises/${exercise.id}/teams` });
        const body: TeamInputDTO = req.request.body;
        expect(body.name).toBe('Team Alpha');
        expect(body.shortName).toBe('alpha');
        expect(body.image).toBe('img.png');
        expect(body.students).toEqual([101, 102]);
        expect(body.ownerId).toBe(201);

        req.flush(expectedTeam);
        tick();
    }));

    it('should update a team and send TeamInputDTO', fakeAsync(() => {
        const team: Team = {
            id: 5,
            name: 'Team Beta',
            shortName: 'beta',
            students: [{ id: 103, login: 'student3' } as User],
        };

        service.update(exercise, team).subscribe((res) => {
            expect(res.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'PUT', url: `api/exercise/exercises/${exercise.id}/teams/${team.id}` });
        const body: TeamInputDTO = req.request.body;
        expect(body.id).toBe(5);
        expect(body.name).toBe('Team Beta');
        expect(body.shortName).toBe('beta');
        expect(body.students).toEqual([103]);
        expect(body.ownerId).toBeUndefined();

        req.flush(team);
        tick();
    }));

    it('should handle team with no students in toInputDTO', fakeAsync(() => {
        const team: Team = {
            name: 'Empty Team',
            shortName: 'empty',
        };

        service.create(exercise, team).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        const body: TeamInputDTO = req.request.body;
        expect(body.students).toEqual([]);
        expect(body.name).toBe('Empty Team');

        req.flush(team);
        tick();
    }));

    it('should find a team', fakeAsync(() => {
        const team: Team = { id: 5, name: 'Team A', shortName: 'a' };

        service.find(exercise, 5).subscribe((res) => {
            expect(res.body).toBeDefined();
            expect(res.body!.id).toBe(5);
        });

        const req = httpMock.expectOne({ method: 'GET', url: `api/exercise/exercises/${exercise.id}/teams/5` });
        req.flush(team);
        tick();
    }));

    it('should find all teams by exercise id', fakeAsync(() => {
        const teams: Team[] = [
            { id: 1, name: 'Team 1', shortName: 't1' },
            { id: 2, name: 'Team 2', shortName: 't2' },
        ];

        service.findAllByExerciseId(exercise.id!).subscribe((res) => {
            expect(res.body).toHaveLength(2);
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe(`api/exercise/exercises/${exercise.id}/teams`);
        req.flush(teams);
        tick();
    }));

    it('should find all teams with team owner filter', fakeAsync(() => {
        service.findAllByExerciseId(exercise.id!, 201).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('teamOwnerId')).toBe('201');
        req.flush([]);
        tick();
    }));

    it('should delete a team', fakeAsync(() => {
        service.delete(exercise, 5).subscribe((res) => {
            expect(res).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'DELETE', url: `api/exercise/exercises/${exercise.id}/teams/5` });
        req.flush(null);
        tick();
    }));

    it('should check if team short name exists', fakeAsync(() => {
        service.existsByShortName(course, 'alpha').subscribe((res) => {
            expect(res.body).toBeTrue();
        });

        const req = httpMock.expectOne({ method: 'GET', url: `api/exercise/courses/${course.id}/teams/exists?shortName=alpha` });
        req.flush(true);
        tick();
    }));

    it('should search in course for exercise team', fakeAsync(() => {
        service.searchInCourseForExerciseTeam(course, exercise, 'student1').subscribe((res) => {
            expect(res.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.url).toBe(`api/exercise/courses/${course.id}/exercises/${exercise.id}/team-search-users?loginOrName=student1`);
        req.flush([]);
        tick();
    }));

    it('should import teams from list and send TeamImportDTOs', fakeAsync(() => {
        const teams: Team[] = [
            {
                name: 'Import Team',
                shortName: 'imp',
                image: 'img.png',
                students: [{ login: 'user1', visibleRegistrationNumber: 'REG001' } as User, { login: 'user2' } as User],
            },
        ];

        service.importTeams(exercise, teams, TeamImportStrategyType.CREATE_ONLY).subscribe((res) => {
            expect(res.body).toBeDefined();
        });

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain('import-from-list');
        expect(req.request.url).toContain('importStrategyType=CREATE_ONLY');

        const body: TeamImportDTO[] = req.request.body;
        expect(body).toHaveLength(1);
        expect(body[0].name).toBe('Import Team');
        expect(body[0].shortName).toBe('imp');
        expect(body[0].students).toHaveLength(2);
        expect(body[0].students[0].login).toBe('user1');
        expect(body[0].students[0].visibleRegistrationNumber).toBe('REG001');
        expect(body[0].students[1].login).toBe('user2');

        req.flush([]);
        tick();
    }));

    it('should import teams with empty students', fakeAsync(() => {
        const teams: Team[] = [
            {
                name: 'Empty Import',
                shortName: 'empty',
            },
        ];

        service.importTeams(exercise, teams, TeamImportStrategyType.CREATE_ONLY).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        const body: TeamImportDTO[] = req.request.body;
        expect(body[0].students).toEqual([]);

        req.flush([]);
        tick();
    }));

    it('should import teams from source exercise', fakeAsync(() => {
        const sourceExercise = { id: 99 } as Exercise;

        service.importTeamsFromSourceExercise(exercise, sourceExercise, TeamImportStrategyType.PURGE_EXISTING).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.url).toContain(`import-from-exercise/${sourceExercise.id}`);
        expect(req.request.url).toContain('importStrategyType=PURGE_EXISTING');

        req.flush([]);
        tick();
    }));

    it('should find course with exercises and participations for team', fakeAsync(() => {
        const team: Team = { id: 1, shortName: 'alpha' };
        const returnedCourse = { id: course.id, exercises: [] } as unknown as Course;

        service.findCourseWithExercisesAndParticipationsForTeam(course, team).subscribe((res) => {
            expect(res.body).toBeDefined();
        });

        const req = httpMock.expectOne({
            method: 'GET',
            url: `api/exercise/courses/${course.id}/teams/${team.shortName}/with-exercises-and-participations`,
        });
        req.flush(returnedCourse);
        tick();
    }));

    it('should export teams', () => {
        jest.spyOn(window, 'Blob').mockImplementation(function (this: Blob) {
            return this;
        } as any);
        const downloadSpy = jest.fn();
        jest.spyOn(require('app/shared/util/download.util'), 'downloadFile').mockImplementation(downloadSpy);

        const teams: Team[] = [
            {
                name: 'Team Export',
                shortName: 'exp',
                students: [{ login: 'user1', firstName: 'First', lastName: 'Last', visibleRegistrationNumber: 'REG001' } as User, { login: 'user2' } as User],
            },
            {
                name: 'Empty Students',
                shortName: 'empty',
            },
        ];

        service.exportTeams(teams);

        expect(downloadSpy).toHaveBeenCalled();
    });

    it('should return resource URL correctly', () => {
        expect(TeamService.resourceUrl(42)).toBe('api/exercise/exercises/42/teams');
    });
});

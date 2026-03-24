import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TeamService } from 'app/exercise/team/team.service';
import { Team, TeamImportStrategyType } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { take } from 'rxjs/operators';

describe('TeamService', () => {
    let service: TeamService;
    let httpMock: HttpTestingController;

    const exercise = { id: 1 } as Exercise;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                TeamService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        });

        service = TestBed.inject(TeamService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should create a team using TeamInputDTO', fakeAsync(() => {
        const team: Team = {
            id: undefined,
            name: 'Team 1',
            shortName: 'T1',
            image: 'img.png',
            students: [
                { id: 10, login: 'student1', internal: true },
                { id: 20, login: 'student2', internal: true },
            ],
            owner: { id: 5, login: 'tutor', internal: true },
        } as Team;

        const returnedTeam = { ...team, id: 1 };

        service
            .create(exercise, team)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toBeDefined();
            });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercise/exercises/${exercise.id}/teams` });
        expect(req.request.body).toEqual({
            id: undefined,
            name: 'Team 1',
            shortName: 'T1',
            image: 'img.png',
            students: [10, 20],
            ownerId: 5,
        });
        req.flush(returnedTeam);
        tick();
    }));

    it('should update a team using TeamInputDTO', fakeAsync(() => {
        const team: Team = {
            id: 1,
            name: 'Team Updated',
            shortName: 'TU',
            students: [{ id: 30, login: 'student3', internal: true }],
        } as Team;

        service
            .update(exercise, team)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toBeDefined();
            });

        const req = httpMock.expectOne({ method: 'PUT', url: `api/exercise/exercises/${exercise.id}/teams/${team.id}` });
        expect(req.request.body).toEqual({
            id: 1,
            name: 'Team Updated',
            shortName: 'TU',
            image: undefined,
            students: [30],
            ownerId: undefined,
        });
        req.flush(team);
        tick();
    }));

    it('should import teams using TeamImportDTO', fakeAsync(() => {
        const teams: Team[] = [
            {
                name: 'Import Team',
                shortName: 'IT',
                students: [{ login: 'student1', visibleRegistrationNumber: '12345', internal: true } as any],
            } as Team,
        ];

        service.importTeams(exercise, teams, TeamImportStrategyType.CREATE_ONLY).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'PUT' });
        expect(req.request.body).toEqual([
            {
                name: 'Import Team',
                shortName: 'IT',
                image: undefined,
                students: [{ login: 'student1', visibleRegistrationNumber: '12345' }],
            },
        ]);
        req.flush([]);
        tick();
    }));
});

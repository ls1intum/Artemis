import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { TeamComponent } from 'app/exercise/team/team.component';
import { TeamService } from 'app/exercise/team/team.service';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { mockExercise, mockTeam, mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { AlertService } from 'app/shared/service/alert.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

describe('TeamComponent', () => {
    let comp: TeamComponent;
    let fixture: ComponentFixture<TeamComponent>;
    let router: Router;
    const user = new User(99, 'newUser', 'UserFirstName', 'UserLastName');
    let accountService: AccountService;
    let identityStub: jest.SpyInstance;
    let exerciseService: ExerciseService;
    let teamService: TeamService;
    let alertService: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                TeamService,
                ExerciseService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ id: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                accountService = TestBed.inject(AccountService);
                identityStub = jest.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
                fixture = TestBed.createComponent(TeamComponent);
                comp = fixture.componentInstance;
                alertService = TestBed.inject(AlertService);
                router = TestBed.inject(Router);
                teamService = TestBed.inject(TeamService);
                exerciseService = TestBed.inject(ExerciseService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('ngOnInit', () => {
        let alertServiceStub: jest.SpyInstance;

        afterEach(() => {
            jest.restoreAllMocks();
        });

        it('should set team and exercise from services and call find on exerciseService to retreive exercise', () => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse<Exercise>({ body: mockExercise })));
            jest.spyOn(teamService, 'find').mockReturnValue(of(new HttpResponse<Team>({ body: mockTeam })));
            comp.ngOnInit();
            expect(comp.exercise).toEqual(mockExercise);
            expect(comp.team).toEqual(mockTeam);
            expect(comp.isTeamOwner).toBeFalse();
            expect(exerciseService['find']).toHaveBeenCalledOnce();
        });

        it('should call alert service error when exercise service fails', () => {
            const exerciseStub = jest.spyOn(exerciseService, 'find').mockReturnValue(throwError(() => ({ status: 404 })));
            alertServiceStub = jest.spyOn(alertService, 'error');
            waitForAsync(() => {
                comp.ngOnInit();
                expect(exerciseStub).toHaveBeenCalledOnce();
                expect(alertServiceStub).toHaveBeenCalledOnce();
                expect(comp.isLoading).toBeFalse();
            });
        });

        it('should call alert service error when team service fails', () => {
            const teamStub = jest.spyOn(teamService, 'find').mockReturnValue(throwError(() => ({ status: 404 })));
            alertServiceStub = jest.spyOn(alertService, 'error');
            waitForAsync(() => {
                comp.ngOnInit();
                expect(teamStub).toHaveBeenCalledOnce();
                expect(alertServiceStub).toHaveBeenCalledOnce();
                expect(comp.isLoading).toBeFalse();
            });
        });
    });

    describe('ngOnInit with team owner', () => {
        it('should set team owner true if user is team owner', () => {
            waitForAsync(() => {
                identityStub.mockReturnValue(Promise.resolve(Object.assign({}, user, { id: 1 })));
                fixture = TestBed.createComponent(TeamComponent);
                comp = fixture.componentInstance;
                comp.ngOnInit();
                expect(comp.isTeamOwner).toBeTrue();
            });
        });
    });

    describe('onTeamUpdate', () => {
        it('should update team to given team', () => {
            comp.onTeamUpdate(mockTeams[1]);
            expect(comp.team).toEqual(mockTeams[1]);
        });
    });

    describe('onTeamDelete', () => {
        it('should go to teams overview on delete', () => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse<Exercise>({ body: mockExercise })));
            jest.spyOn(teamService, 'find').mockReturnValue(of(new HttpResponse<Team>({ body: mockTeam })));
            comp.ngOnInit();
            jest.spyOn(router, 'navigate');
            comp.onTeamDelete();
            expect(router['navigate']).toHaveBeenCalledOnce();
            expect(router['navigate']).toHaveBeenCalledWith(['/course-management', mockExercise.course?.id, 'exercises', mockExercise.id, 'teams']);
        });
    });
});

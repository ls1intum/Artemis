import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { mockExercise, mockTeam, mockTeams, TeamRequestInterceptorMock } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { AlertService } from 'app/core/util/alert.service';

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
            imports: [ArtemisTestModule, MockModule(NgbModule), MockModule(FeatureToggleModule), MockModule(NgxDatatableModule), MockModule(RouterModule)],
            declarations: [
                TeamComponent,
                MockComponent(TeamUpdateButtonComponent),
                MockComponent(TeamDeleteButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(TeamParticipationTableComponent),
                MockComponent(DataTableComponent),
                MockComponent(AssessmentWarningComponent),
            ],
            providers: [
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(AccountService),
                TeamService,
                MockProvider(TranslateService),
                ExerciseService,
                {
                    provide: HTTP_INTERCEPTORS,
                    useClass: TeamRequestInterceptorMock,
                    multi: true,
                },
                MockProvider(Router),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: of({ teamId: mockTeam.id, exerciseId: mockExercise.id }),
                    },
                },
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
            jest.spyOn(exerciseService, 'find');
            comp.ngOnInit();
            expect(comp.exercise).toEqual(mockExercise);
            expect(comp.team).toEqual(mockTeam);
            expect(comp.isTeamOwner).toBeFalse();
            expect(exerciseService['find']).toHaveBeenCalled();
        });

        it('should call alert service error when exercise service fails', () => {
            const exerciseStub = jest.spyOn(exerciseService, 'find').mockReturnValue(throwError(() => ({ status: 404 })));
            alertServiceStub = jest.spyOn(alertService, 'error');
            waitForAsync(() => {
                comp.ngOnInit();
                expect(exerciseStub).toHaveBeenCalled();
                expect(alertServiceStub).toHaveBeenCalled();
                expect(comp.isLoading).toBeFalse();
            });
        });

        it('should call alert service error when team service fails', () => {
            const teamStub = jest.spyOn(teamService, 'find').mockReturnValue(throwError(() => ({ status: 404 })));
            alertServiceStub = jest.spyOn(alertService, 'error');
            waitForAsync(() => {
                comp.ngOnInit();
                expect(teamStub).toHaveBeenCalled();
                expect(alertServiceStub).toHaveBeenCalled();
                expect(comp.isLoading).toBeFalse();
            });
        });
    });

    describe('ngOnInit with team owner', () => {
        it('should set team owner true if user is team owner', () => {
            waitForAsync(() => {
                identityStub.mockReturnValue(Promise.resolve({ ...user, id: 1 }));
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
            comp.ngOnInit();
            jest.spyOn(router, 'navigate');
            comp.onTeamDelete();
            expect(router['navigate']).toHaveBeenCalledOnce();
            expect(router['navigate']).toHaveBeenCalledWith(['/course-management', mockExercise.course?.id, 'exercises', mockExercise.id, 'teams']);
        });
    });
});

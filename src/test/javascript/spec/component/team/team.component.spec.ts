import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpTestingController } from '@angular/common/http/testing';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, Router, RouterModule } from '@angular/router';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipe } from '@ngx-translate/core';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamComponent } from 'app/exercises/shared/team/team.component.ts';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { FeatureToggleModule } from 'app/shared/feature-toggle/feature-toggle.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe.ts';
import * as chai from 'chai';
import { NgJhipsterModule } from 'ng-jhipster';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { restore, SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { mockExercise, mockTeam, mockTeams, TeamRequestInterceptorMock } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamComponent', () => {
    let comp: TeamComponent;
    let fixture: ComponentFixture<TeamComponent>;
    let debugElement: DebugElement;
    let httpTestingController: HttpTestingController;
    let router: Router;
    let user = new User(99, 'newUser', 'UserFirstName', 'UserLastName');
    let accountService: AccountService;
    let identityStub: SinonStub;
    function resetComponent() {
        comp.isLoading = false;
        comp.isTransitioning = false;
        comp.isAdmin = false;
        comp.isTeamOwner = false;
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [
                    ArtemisTestModule,
                    MockModule(NgbModule),
                    MockModule(NgJhipsterModule),
                    MockModule(FeatureToggleModule),
                    MockModule(NgxDatatableModule),
                    MockModule(RouterModule),
                ],
                declarations: [
                    TeamComponent,
                    ButtonComponent,
                    MockComponent(TeamUpdateButtonComponent),
                    MockComponent(TeamDeleteButtonComponent),
                    MockPipe(TranslatePipe),
                    MockPipe(ArtemisDatePipe),
                    MockComponent(TeamParticipationTableComponent),
                    MockComponent(DataTableComponent),
                    MockComponent(AlertComponent),
                ],
                providers: [
                    MockProvider(SessionStorageService),
                    MockProvider(LocalStorageService),
                    MockProvider(AccountService),
                    TeamService,
                    ExerciseService,
                    {
                        provide: HTTP_INTERCEPTORS,
                        useClass: TeamRequestInterceptorMock,
                        multi: true,
                    },
                    { provide: Router, useClass: MockRouter },
                    {
                        provide: ActivatedRoute,
                        useValue: {
                            params: of({ teamId: mockTeam.id, exerciseId: mockExercise.id }),
                        },
                    },
                ],
            }).compileComponents();
            accountService = TestBed.inject(AccountService);
            identityStub = stub(accountService, 'identity').returns(Promise.resolve(user));
            stub(accountService, 'isAtLeastTutorInCourse').returns(true);
            stub(accountService, 'isAtLeastInstructorInCourse').returns(true);
            httpTestingController = TestBed.inject(HttpTestingController);
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        restore();
    });

    describe('ngOnInit', () => {
        waitForAsync(() => {
            beforeEach(() => {
                fixture = TestBed.createComponent(TeamComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                resetComponent();
            });
        });
        afterEach(() => {
            restore();
        });
        it('should set team and exercise from services', () => {
            comp.ngOnInit();
            expect(comp.exercise).to.deep.equal(mockExercise);
            expect(comp.team).to.deep.equal(mockTeam);
            expect(comp.exercise.isAtLeastTutor).equal(true);
            expect(comp.exercise.isAtLeastInstructor).equal(true);
            expect(comp.isTeamOwner).to.equal(false);
        });
        it('should set team owner true if user is team owner', () => {
            waitForAsync(() => {
                identityStub.returns(Promise.resolve({ ...user, id: 1 }));
                fixture = TestBed.createComponent(TeamComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                comp.ngOnInit();
                expect(comp.isTeamOwner).to.equal(true);
            });
        });
    });

    describe('onTeamUpdate', () => {
        it('should update team to given team', () => {
            comp.onTeamUpdate(mockTeams[1]);
            expect(comp.team).to.deep.equal(mockTeams[1]);
        });
    });
});

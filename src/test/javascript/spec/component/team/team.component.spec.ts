import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { RouterTestingModule } from '@angular/router/testing';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { of } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import * as sinon from 'sinon';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslatePipe } from '@ngx-translate/core';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { JhiTranslateDirective } from 'ng-jhipster';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { AccountService } from 'app/core/auth/account.service';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { ActivatedRoute, Params } from '@angular/router';
import * as moment from 'moment';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamComponent', () => {
    let teamComponentFixture: ComponentFixture<TeamComponent>;
    let component: TeamComponent;
    let user: User;
    let course: Course;
    let exercise: Exercise;

    let team: Team;

    beforeEach(() => {
        course = new Course();
        course.id = 1;
        user = new User();
        user.id = 1234;

        team = new Team();
        team.id = 1;
        team.createdDate = moment();

        exercise = {
            id: 1,
            isAtLeastInstructor: true,
            isAtLeastTutor: true,
            teams: [team],
            course,
        } as Exercise;

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), ArtemisDataTableModule, NgbModalModule, NgxDatatableModule, FontAwesomeTestingModule, TranslateModule.forRoot()],
            declarations: [
                TeamComponent,
                MockComponent(AlertComponent),
                MockComponent(TeamUpdateButtonComponent),
                MockComponent(TeamDeleteButtonComponent),
                MockComponent(TeamParticipationTableComponent),
                MockPipe(TranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(NgbTooltip),
            ],
            providers: [
                MockProvider(SessionStorageService),
                MockDirective(JhiTranslateDirective),
                MockProvider(HttpClient),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: {
                            subscribe: (fn: (value: Params) => void) =>
                                fn({
                                    exerciseId: 20,
                                    teamId: 1,
                                }),
                        },
                    },
                },
                MockProvider(TeamService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: team,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(ExerciseService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: exercise,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(AccountService, {
                    identity: () => Promise.resolve(user),
                }),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                teamComponentFixture = TestBed.createComponent(TeamComponent);
                component = teamComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        const exerciseService = TestBed.inject(ExerciseService);
        const teamService = TestBed.inject(TeamService);

        const exerciseServiceSpy = sinon.spy(exerciseService, 'find');
        const teamServiceSpy = sinon.spy(teamService, 'find');
        teamComponentFixture.detectChanges();

        expect(teamComponentFixture).to.be.ok;
        expect(exerciseServiceSpy).to.be.calledOnce;
        exerciseService.find(20).subscribe((result) => {
            expect(result.body).to.equal(exercise);
        });
        expect(teamServiceSpy).to.be.calledOnce;
        teamService.find(exercise, 1).subscribe((result) => {
            expect(result.body).to.equal(team);
        });
    });

    it('onTeamUpdate should set the updated Team', () => {
        teamComponentFixture.detectChanges();
        component.onTeamUpdate(team);
        expect(component.team).to.equal(team);
    });
});

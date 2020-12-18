import { TeamComponent } from 'app/exercises/shared/team/team.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise } from 'app/entities/exercise.model';
import { User } from 'app/core/user/user.model';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { of } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import * as sinon from 'sinon';
import { Team } from 'app/entities/team.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-students-list/team-students-list.component';
import { TeamStudentsOnlineListComponent } from 'app/exercises/shared/team/team-students-online-list/team-students-online-list.component';
import { TeamParticipateInfoBoxComponent } from 'app/exercises/shared/team/team-participate-info-box/team-participate-info-box.component';
import { TeamParticipationTableComponent } from 'app/exercises/shared/team/team-participation-table/team-participation-table.component';
import { TeamsImportFromFileFormComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-from-file-form.component';
import { TranslatePipe } from '@ngx-translate/core';
import { JhiTranslateDirective } from 'ng-jhipster';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { AccountService } from 'app/core/auth/account.service';
import { ActivatedRoute, Params } from '@angular/router';
import * as moment from 'moment';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamComponent', () => {
    let teamComponentFixture: ComponentFixture<TeamComponent>;
    let component: TeamComponent;
    let user: User;
    let exercise: Exercise;

    let team: Team;

    beforeEach(() => {
        user = new User();
        user.id = 1234;

        team = new Team();
        team.id = 1;
        team.createdDate = moment();

        exercise = {
            id: 20,
            isAtLeastInstructor: true,
            isAtLeastTutor: true,
            teams: [team],
        } as Exercise;

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), ArtemisSharedModule, NgxDatatableModule, ArtemisDataTableModule, ArtemisSharedComponentModule, ArtemisResultModule],
            declarations: [
                TeamComponent,
                TeamUpdateButtonComponent,
                TeamUpdateDialogComponent,
                TeamsImportButtonComponent,
                TeamsExportButtonComponent,
                TeamsImportDialogComponent,
                TeamDeleteButtonComponent,
                TeamStudentSearchComponent,
                TeamOwnerSearchComponent,
                TeamExerciseSearchComponent,
                TeamStudentsListComponent,
                TeamStudentsOnlineListComponent,
                TeamParticipateInfoBoxComponent,
                TeamParticipationTableComponent,
                TeamsImportFromFileFormComponent,
                MockPipe(TranslatePipe),
            ],
            providers: [
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
                MockProvider(AccountService, {
                    identity: () => Promise.resolve(user),
                }),
                MockProvider(SessionStorageService),
                MockDirective(JhiTranslateDirective),
                MockProvider(HttpClient),
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
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
        expect(teamServiceSpy).to.be.calledOnce;
    });

    it('onTeamUpdate', () => {
        expect(component.onTeamUpdate(team)).to.equal(team);
    });
});

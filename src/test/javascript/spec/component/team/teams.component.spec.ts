import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsComponent } from 'app/exercise/team/teams/teams.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { of } from 'rxjs';
import { mockTeams, MockTeamService } from '../../helpers/mocks/service/mock-team.service';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockComponent } from 'ng-mocks';
import { MockTranslateService, TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TeamsExportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-export-button.component';
import { TeamsImportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-import-button.component';
import { TeamUpdateButtonComponent } from 'app/exercise/team/team-update-dialog/team-update-button.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { TeamStudentsListComponent } from 'app/exercise/team/team-participate/team-students-list.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { TeamDeleteButtonComponent } from 'app/exercise/team/team-update-dialog/team-delete-button.component';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

describe('TeamsComponent', () => {
    let comp: TeamsComponent;
    let fixture: ComponentFixture<TeamsComponent>;
    let debugElement: DebugElement;

    const route = {
        params: of({ exerciseId: 1 }),
        snapshot: { queryParamMap: convertToParamMap({}) },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [NgxDatatableModule],
            declarations: [
                TeamsComponent,
                TranslatePipeMock,
                MockComponent(TeamsExportButtonComponent),
                MockComponent(TeamsImportButtonComponent),
                MockComponent(TeamUpdateButtonComponent),
                MockComponent(DataTableComponent),
                MockComponent(TeamStudentsListComponent),
                MockRouterLinkDirective,
                MockComponent(TeamDeleteButtonComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: TeamService, useClass: MockTeamService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
            });
    });

    it('Teams are loaded correctly', fakeAsync(() => {
        comp.ngOnInit();
        tick();

        // Make sure that all 3 teams were received for exercise
        expect(comp.teams).toHaveLength(mockTeams.length);

        // Check that ngx-datatable is present
        const datatable = debugElement.query(By.css('jhi-data-table'));
        expect(datatable).not.toBeNull();
    }));
});

import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { NgModel } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { NgxDatatableModule } from '@flaviosantoro92/ngx-datatable';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-participate/team-students-list.component';
import { TeamDeleteButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-delete-button.component';
import { TeamUpdateButtonComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-button.component';
import { teamRoute } from 'app/exercises/shared/team/team.route';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-export-button.component';
import { TeamsImportButtonComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-button.component';
import { TeamsComponent } from 'app/exercises/shared/team/teams.component';
import { DataTableComponent } from 'app/shared/data-table/data-table.component';
import * as ace from 'brace';
import { MockComponent, MockDirective, MockModule } from 'ng-mocks';
import { of } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockExerciseService } from '../../helpers/mocks/service/mock-exercise.service';
import { MockParticipationService } from '../../helpers/mocks/service/mock-participation.service';
import { MockTeamService, mockTeams } from '../../helpers/mocks/service/mock-team.service';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';

describe('TeamsComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');

    let comp: TeamsComponent;
    let fixture: ComponentFixture<TeamsComponent>;
    let debugElement: DebugElement;
    let router: Router;

    const route = {
        params: of({ exerciseId: 1 }),
        snapshot: { queryParamMap: convertToParamMap({}) },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(NgxDatatableModule), RouterTestingModule.withRoutes([teamRoute[0]])],
            declarations: [
                TeamsComponent,
                MockDirective(NgModel),
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
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamsComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                router = debugElement.injector.get(Router);
                fixture.ngZone!.run(() => {
                    router.initialNavigation();
                });
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

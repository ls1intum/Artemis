import { expect, vi } from 'vitest';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-export-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
describe('TeamsExportButtonComponent', () => {
    setupTestBed({ zoneless: true });
    let comp: TeamsExportButtonComponent;
    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;

    function resetComponent() {
        comp.teams = mockTeams;
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockModule(NgbModule), MockDirective(FeatureToggleDirective)],
            providers: [MockProvider(TeamService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
    });
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        teamService = TestBed.inject(TeamService);
    });

    describe('exportTeams', () => {
        let exportTeamsStub: ReturnType<typeof vi.spyOn>;
        beforeEach(() => {
            resetComponent();
            exportTeamsStub = vi.spyOn(teamService, 'exportTeams');
        });
        afterEach(() => {
            vi.restoreAllMocks();
        });
        it('should call export teams from team service when called', () => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(exportTeamsStub).toHaveBeenCalledOnce();
        });
    });
});

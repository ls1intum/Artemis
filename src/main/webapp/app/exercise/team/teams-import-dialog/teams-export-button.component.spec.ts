import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-export-button.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TeamsExportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                MockModule(NgbModule),
                MockDirective(FeatureToggleDirective),
                TeamsExportButtonComponent,
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [MockProvider(TeamService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        debugElement = fixture.debugElement;
        teamService = TestBed.inject(TeamService);
        fixture.componentRef.setInput('teams', mockTeams);
        fixture.detectChanges(false);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('exportTeams', () => {
        it('should call export teams from team service when called', () => {
            const exportTeamsStub = vi.spyOn(teamService, 'exportTeams').mockImplementation(() => {});
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(exportTeamsStub).toHaveBeenCalledOnce();
            expect(exportTeamsStub).toHaveBeenCalledWith(mockTeams);
        });
    });
});

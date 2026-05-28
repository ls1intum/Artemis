import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-export-button.component';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TeamsExportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TeamsExportButtonComponent],
            providers: [MockProvider(TeamService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        debugElement = fixture.debugElement;
        teamService = TestBed.inject(TeamService);
        fixture.componentRef.setInput('teams', mockTeams);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('exportTeams', () => {
        it('should call export teams from team service when called', () => {
            const exportTeamsStub = vi.spyOn(teamService, 'exportTeams');

            const button = debugElement.nativeElement.querySelector('button');
            button.click();

            expect(exportTeamsStub).toHaveBeenCalledOnce();
            expect(exportTeamsStub).toHaveBeenCalledWith(mockTeams);
        });
    });
});

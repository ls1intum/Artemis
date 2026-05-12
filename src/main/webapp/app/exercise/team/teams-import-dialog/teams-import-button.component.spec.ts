import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsImportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-import-button.component';
import { TeamsImportDialogComponent } from 'app/exercise/team/teams-import-dialog/teams-import-dialog.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockExercise, mockSourceTeams, mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('TeamsImportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamsImportButtonComponent;
    let fixture: ComponentFixture<TeamsImportButtonComponent>;
    let debugElement: DebugElement;
    let dialogServiceOpenSpy: ReturnType<typeof vi.fn>;

    beforeEach(async () => {
        dialogServiceOpenSpy = vi.fn().mockReturnValue({ onClose: of(mockSourceTeams) });

        await TestBed.configureTestingModule({
            imports: [
                MockModule(NgbModule),
                MockDirective(FeatureToggleDirective),
                TeamsImportButtonComponent,
                ButtonComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
            ],
            providers: [
                MockProvider(TeamService),
                { provide: DialogService, useValue: { open: dialogServiceOpenSpy } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('teams', mockTeams);
        fixture.detectChanges(false);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('openTeamsImportDialog', () => {
        it('should open teams import dialog with correct config when clicked', () => {
            let emittedTeams: Team[] | undefined;
            comp.save.subscribe((value: Team[]) => {
                emittedTeams = value;
            });

            const button = debugElement.nativeElement.querySelector('button');
            button.click();

            expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
            const [openedComponent, openedConfig] = dialogServiceOpenSpy.mock.calls[0];
            expect(openedComponent).toBe(TeamsImportDialogComponent);
            expect(openedConfig.data).toEqual({ exercise: mockExercise, teams: mockTeams });
            expect(openedConfig.width).toBe('50rem');
            expect(openedConfig.modal).toBe(true);
            expect(openedConfig.closeOnEscape).toBe(true);
            expect(openedConfig.dismissableMask).toBe(false);
            expect(openedConfig.header).toContain(mockExercise.title);

            expect(emittedTeams).toEqual(mockSourceTeams);
        });

        it('should not emit save when dialog is dismissed (undefined result)', () => {
            dialogServiceOpenSpy.mockReturnValueOnce({ onClose: of(undefined) });
            let saveCalled = false;
            comp.save.subscribe(() => {
                saveCalled = true;
            });

            const button = debugElement.nativeElement.querySelector('button');
            button.click();

            expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
            expect(saveCalled).toBe(false);
        });
    });
});

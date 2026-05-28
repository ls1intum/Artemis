import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamsImportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-import-button.component';
import { TeamsImportDialogComponent } from 'app/exercise/team/teams-import-dialog/teams-import-dialog.component';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { mockExercise, mockSourceTeams, mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TeamsImportButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamsImportButtonComponent;
    let fixture: ComponentFixture<TeamsImportButtonComponent>;
    let debugElement: DebugElement;
    let dialogService: DialogService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [TeamsImportButtonComponent],
            providers: [MockProvider(DialogService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamsImportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        dialogService = TestBed.inject(DialogService);
        fixture.componentRef.setInput('teams', mockTeams);
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('openTeamsImportDialog', () => {
        it('should open teams import dialog when called', () => {
            const onClose = new Subject<Team[] | undefined>();
            const dialogRef = { onClose: onClose.asObservable() } as DynamicDialogRef;
            const dialogServiceStub = vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
            const teams: Team[][] = [];
            comp.save.subscribe((value: Team[]) => teams.push(value));

            const button = debugElement.nativeElement.querySelector('button');
            button.click();

            expect(dialogServiceStub).toHaveBeenCalledOnce();
            expect(dialogServiceStub).toHaveBeenCalledWith(
                TeamsImportDialogComponent,
                expect.objectContaining({
                    showHeader: false,
                    width: '50rem',
                    modal: true,
                    closeOnEscape: true,
                    dismissableMask: false,
                    data: { exercise: mockExercise, teams: mockTeams },
                }),
            );

            onClose.next(mockSourceTeams);

            expect(teams).toEqual([mockSourceTeams]);
        });
    });
});

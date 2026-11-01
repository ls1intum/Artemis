import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SubmissionExportDialogComponent } from 'app/exercise/submission-export/dialog/submission-export-dialog.component';
import { SubmissionExportButtonComponent } from 'app/exercise/submission-export/button/submission-export-button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogService } from '@tumaet/ui-angular';

describe('Submission Export Button Component', () => {
    let fixture: ComponentFixture<SubmissionExportButtonComponent>;
    let component: SubmissionExportButtonComponent;
    let dialogService: TumAetUiDialogService;
    let mouseEvent: MouseEvent;

    const exerciseId = 1;
    const exerciseType = ExerciseType.TEXT;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SubmissionExportButtonComponent],
            providers: [
                { provide: TumAetUiDialogService, useValue: { open: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(SubmissionExportButtonComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(SubmissionExportButtonComponent);
        component = fixture.componentInstance;
        dialogService = TestBed.inject(TumAetUiDialogService);

        fixture.componentRef.setInput('exerciseId', exerciseId);
        fixture.componentRef.setInput('exerciseType', exerciseType);
        fixture.detectChanges();

        mouseEvent = new MouseEvent('mouseEvent');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open export submission dialog', () => {
        const mouseEventSpy = vi.spyOn(mouseEvent, 'stopPropagation');
        const openSpy = vi.spyOn(dialogService, 'open');
        const buttonPressed = vi.spyOn(component.buttonPressed, 'emit');

        component.openSubmissionExportDialog(mouseEvent);

        expect(buttonPressed).toHaveBeenCalledOnce();

        expect(mouseEventSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledOnce();
        expect(openSpy).toHaveBeenCalledWith(
            SubmissionExportDialogComponent,
            expect.objectContaining({
                header: 'artemisApp.instructorDashboard.exportSubmissions.title',
                size: 'large',
                closable: true,
                closeOnEscape: true,
                dismissableMask: true,
            }),
        );
    });

    it('should preserve the known exam source when opening the export dialog', () => {
        fixture.componentRef.setInput('examExercise', true);
        component.openSubmissionExportDialog(mouseEvent);
        expect(vi.mocked(dialogService.open).mock.calls[0][1]?.inputValues).toEqual({ exerciseId, exerciseType, examExercise: true });
    });

    it('should set input values for dialog', () => {
        const openSpy = vi.spyOn(dialogService, 'open');

        component.openSubmissionExportDialog(mouseEvent);

        expect(openSpy.mock.calls[0][1]?.inputValues).toEqual({ exerciseId, exerciseType, examExercise: false });
    });
});

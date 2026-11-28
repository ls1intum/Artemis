import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiDialogConfig, TumAetUiDialogRef } from '@tumaet/ui-angular/dialog';
import { ApollonDiagramImportDialogComponent } from 'app/quiz/manage/apollon-diagrams/import-dialog/apollon-diagram-import-dialog.component';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';

describe('ApollonDiagramImportDialog Component', () => {
    let fixture: ComponentFixture<ApollonDiagramImportDialogComponent>;
    let dialogRef: TumAetUiDialogRef;
    const apollonDiagramId = 5;
    const dialogRefMock = { close: vi.fn() } as unknown as TumAetUiDialogRef;
    const dialogConfigMock = new TumAetUiDialogConfig({ data: { courseId: 123 } });

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ApollonDiagramImportDialogComponent],
            providers: [
                { provide: TumAetUiDialogRef, useValue: dialogRefMock },
                { provide: TumAetUiDialogConfig, useValue: dialogConfigMock },
            ],
        })
            .overrideTemplate(ApollonDiagramImportDialogComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonDiagramImportDialogComponent);
                dialogRef = TestBed.inject(TumAetUiDialogRef);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should read the courseId from the dialog config on init', () => {
        fixture.detectChanges();
        expect(fixture.componentInstance.courseId()).toBe(123);
    });

    it('handleDetailOpen', () => {
        fixture.componentInstance.handleDetailOpen(apollonDiagramId);
        expect(fixture.componentInstance.isInEditView()).toBeTruthy();
        expect(fixture.componentInstance.apollonDiagramDetailId()).toBe(5);
    });

    it('handleDetailClose', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        const newDnDQuestion = new DragAndDropQuestion();
        fixture.componentInstance.handleDetailClose(newDnDQuestion);
        expect(closeSpy).toHaveBeenCalledWith(newDnDQuestion);

        fixture.componentInstance.handleDetailClose();
        expect(fixture.componentInstance.isInEditView()).toBeFalsy();
    });

    it('closeModal', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');
        fixture.componentInstance.closeModal();
        expect(closeSpy).toHaveBeenCalledOnce();
    });
});

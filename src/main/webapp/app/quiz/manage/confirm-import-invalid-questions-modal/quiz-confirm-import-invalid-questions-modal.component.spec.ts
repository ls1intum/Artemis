import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { QuizConfirmImportInvalidQuestionsModalComponent } from './quiz-confirm-import-invalid-questions-modal.component';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('QuizConfirmImportInvalidQuestionsModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QuizConfirmImportInvalidQuestionsModalComponent;
    let fixture: ComponentFixture<QuizConfirmImportInvalidQuestionsModalComponent>;
    let dialogRef: DynamicDialogRef;

    const invalidQuestions: ValidationReason[] = [
        { translateKey: 'invalidQuestion1', translateValues: {} },
        { translateKey: 'invalidQuestion2', translateValues: { someValue: 'test' } },
    ];
    const dialogRefMock = { close: vi.fn() } as unknown as DynamicDialogRef;
    const dialogConfigMock = { data: { invalidFlaggedQuestions: invalidQuestions } } as DynamicDialogConfig;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRefMock },
                { provide: DynamicDialogConfig, useValue: dialogConfigMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(QuizConfirmImportInvalidQuestionsModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizConfirmImportInvalidQuestionsModalComponent);
        component = fixture.componentInstance;
        dialogRef = TestBed.inject(DynamicDialogRef);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have icons defined', () => {
        expect(component.faBan).toBeDefined();
        expect(component.faTimes).toBeDefined();
        expect(component.faExclamationTriangle).toBeDefined();
    });

    it('should read the invalid flagged questions from the dialog config on init', () => {
        fixture.detectChanges();

        expect(component.invalidFlaggedQuestions).toEqual(invalidQuestions);
        expect(component.invalidFlaggedQuestions.length).toBe(2);
    });

    it('should close the dialog with confirmation on importQuestions', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.importQuestions();

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith(true);
    });

    it('should close the dialog without a result on closeModal', () => {
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.closeModal();

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith();
    });
});

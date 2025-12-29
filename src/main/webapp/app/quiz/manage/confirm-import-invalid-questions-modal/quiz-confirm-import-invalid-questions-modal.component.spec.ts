import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { MockProvider } from 'ng-mocks';
import { QuizConfirmImportInvalidQuestionsModalComponent } from './quiz-confirm-import-invalid-questions-modal.component';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('QuizConfirmImportInvalidQuestionsModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: QuizConfirmImportInvalidQuestionsModalComponent;
    let fixture: ComponentFixture<QuizConfirmImportInvalidQuestionsModalComponent>;
    let activeModal: NgbActiveModal;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideTemplate(QuizConfirmImportInvalidQuestionsModalComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizConfirmImportInvalidQuestionsModalComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should have icons defined', () => {
        expect(component.faBan).toBeDefined();
        expect(component.faTimes).toBeDefined();
        expect(component.faExclamationTriangle).toBeDefined();
    });

    it('should have activeModal injected', () => {
        expect(component.activeModal).toBeDefined();
    });

    it('should close modal and emit shouldImport on importQuestions', () => {
        const closeSpy = vi.spyOn(activeModal, 'close');
        const emitSpy = vi.spyOn(component.shouldImport, 'emit');

        component.importQuestions();

        expect(closeSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledOnce();
    });

    it('should dismiss modal with cancel on closeModal', () => {
        const dismissSpy = vi.spyOn(activeModal, 'dismiss');

        component.closeModal();

        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('should store invalid flagged questions', () => {
        const invalidQuestions: ValidationReason[] = [
            { translateKey: 'invalidQuestion1', translateValues: {} },
            { translateKey: 'invalidQuestion2', translateValues: { someValue: 'test' } },
        ];

        component.invalidFlaggedQuestions = invalidQuestions;

        expect(component.invalidFlaggedQuestions).toEqual(invalidQuestions);
        expect(component.invalidFlaggedQuestions.length).toBe(2);
    });

    it('should allow subscribing to shouldImport event', () => {
        let eventEmitted = false;
        component.shouldImport.subscribe(() => {
            eventEmitted = true;
        });

        component.importQuestions();

        expect(eventEmitted).toBeTrue();
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AiQuizGenerationModalComponent } from 'app/quiz/manage/ai-quiz-generation-modal/ai-quiz-generation-modal.component';
import { AiGeneratedQuestionDTO, AiQuizGenerationService, AiRequestedSubtype } from 'app/quiz/manage/service/ai-quiz-generation.service';
import { TranslateModule } from '@ngx-translate/core';

describe('AiQuizGenerationModalComponent', () => {
    let fixture: ComponentFixture<AiQuizGenerationModalComponent>;
    let comp: AiQuizGenerationModalComponent;
    let service: jest.Mocked<AiQuizGenerationService>;
    let activeModal: jest.Mocked<NgbActiveModal>;

    beforeEach(async () => {
        service = {
            generate: jest.fn(),
        } as unknown as jest.Mocked<AiQuizGenerationService>;

        activeModal = {
            close: jest.fn(),
            dismiss: jest.fn(),
        } as unknown as jest.Mocked<NgbActiveModal>;

        await TestBed.configureTestingModule({
            imports: [AiQuizGenerationModalComponent, TranslateModule.forRoot()],

            providers: [
                { provide: AiQuizGenerationService, useValue: service },
                { provide: NgbActiveModal, useValue: activeModal },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AiQuizGenerationModalComponent);
        comp = fixture.componentInstance;
        comp.courseId = 42;
    });

    it('should call service.generate() and populate generated/warnings', () => {
        const mockResponse = {
            questions: [
                {
                    title: 'Q1',
                    text: 'T',
                    subtype: AiRequestedSubtype.SINGLE_CORRECT,
                    options: [{ text: 'A', correct: true }],
                    tags: [],
                    competencyIds: [],
                } as AiGeneratedQuestionDTO,
            ],
            warnings: ['warn'],
        };

        service.generate.mockReturnValue(of(mockResponse));

        comp.generate({ valid: true } as any);

        expect(service.generate).toHaveBeenCalledWith(42, expect.any(Object));
        expect(comp.generated().length).toBe(1);
        expect(comp.warnings()).toContain('warn');
    });

    it('should close modal with selected questions on useInEditor()', () => {
        comp.generated.set([
            {
                title: 'Q1',
                text: 'T',
                subtype: AiRequestedSubtype.SINGLE_CORRECT,
                options: [{ text: 'A', correct: true }],
                tags: [],
                competencyIds: [],
            } as AiGeneratedQuestionDTO,
        ]);
        comp.selected = { 0: true };

        comp.useInEditor();

        expect(activeModal.close).toHaveBeenCalledWith(
            expect.objectContaining({
                questions: [expect.objectContaining({ title: 'Q1', text: 'T' })],
                requestedSubtype: expect.anything(),
            }),
        );
    });

    it('should dismiss modal on cancel()', () => {
        comp.cancel();
        expect(activeModal.dismiss).toHaveBeenCalled();
    });
});

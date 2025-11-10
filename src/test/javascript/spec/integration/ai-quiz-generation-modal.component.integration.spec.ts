import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AiQuizGenerationModalComponent } from 'app/quiz/manage/ai-quiz-generation-modal/ai-quiz-generation-modal.component';
import { AiDifficultyLevel, AiGeneratedQuestionDTO, AiQuizGenerationService, AiRequestedSubtype } from 'app/quiz/manage/service/ai-quiz-generation.service';
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

        (service.generate as jest.Mock).mockReturnValue(of(mockResponse));

        const mockForm = {
            valid: true,
            value: {
                topic: 'Test Topic',
                numberOfQuestions: 5,
                difficulty: AiDifficultyLevel.MEDIUM,
                subtype: AiRequestedSubtype.SINGLE_CORRECT,
            },
        };
        comp.generate(mockForm as any);
        expect(service.generate).toHaveBeenCalledExacltyOnceWith(42, expect.any(Object));
        expect(comp.generated().length).toBe(1);
        expect(comp.warnings()).toContain('warn');
    });

    it('should not call service.generate when form is invalid', () => {
        comp.generate({ valid: false } as any);
        expect(service.generate).not.toHaveBeenCalled();
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

        expect(activeModal.close).toHaveBeenCalledExactlyOnceWith(
            expect.objectContaining({
                questions: [
                    expect.objectContaining({
                        title: 'Q1',
                        text: 'T',
                        subtype: AiRequestedSubtype.SINGLE_CORRECT,
                        options: [{ text: 'A', correct: true }],
                    }),
                ],
                requestedSubtype: AiRequestedSubtype.SINGLE_CORRECT,
            }),
        );
    });

    it('should dismiss modal on cancel()', () => {
        comp.cancel();
        expect(activeModal.dismiss).toHaveBeenCalled();
    });

    it('anySelected should be false when no generated questions are selected', () => {
        comp.generated.set([
            {
                title: 'Q1',
                text: 'T',
                subtype: AiRequestedSubtype.SINGLE_CORRECT,
                options: [{ text: 'A', correct: true }],
                tags: [],
                competencyIds: [],
            },
        ]);
        comp.selected = { 0: false };

        expect(comp.anySelected).toBeFalse();
    });

    it('should return default subtype label key for unknown subtype', () => {
        const key = comp.subtypeLabelKey('UNKNOWN');
        expect(key).toBe('artemisApp.quizExercise.aiGeneration.subtypes.single');
    });

    it('difficultyToSlider should map all difficulty levels', () => {
        expect(comp.difficultyToSlider(AiDifficultyLevel.EASY)).toBe(0);
        expect(comp.difficultyToSlider(AiDifficultyLevel.MEDIUM)).toBe(1);
        expect(comp.difficultyToSlider(AiDifficultyLevel.HARD)).toBe(2);
    });

    it('sliderToDifficulty should map 0/1/2 correctly', () => {
        expect(comp.sliderToDifficulty(0)).toBe(AiDifficultyLevel.EASY);
        expect(comp.sliderToDifficulty(1)).toBe(AiDifficultyLevel.MEDIUM);
        expect(comp.sliderToDifficulty(2)).toBe(AiDifficultyLevel.HARD);
    });

    it('should show warnings even when no questions are returned', () => {
        const mockResponse = {
            questions: [],
            warnings: ['No questions were generated'],
        };

        (service.generate as jest.Mock).mockReturnValue(of(mockResponse));

        comp.generate({ valid: true } as any);

        expect(service.generate).toHaveBeenCalledOnce();
        expect(comp.generated().length).toBe(0);
        expect(comp.warnings()).toContain('No questions were generated');
    });
});

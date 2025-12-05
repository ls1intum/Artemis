import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { AiQuizGenerationModalComponent } from './ai-quiz-generation-modal.component';
import { AiDifficultyLevel, AiLanguage, AiQuizGenerationResponse, AiQuizGenerationService, AiRequestedSubtype } from '../service/ai-quiz-generation.service';

describe('AiQuizGenerationModalComponent', () => {
    let fixture: ComponentFixture<AiQuizGenerationModalComponent>;
    let comp: AiQuizGenerationModalComponent;
    let mockService: jest.Mocked<AiQuizGenerationService>;
    let mockModal: { close: jest.Mock; dismiss: jest.Mock };

    beforeEach(async () => {
        mockService = {
            generate: jest.fn(),
        } as unknown as jest.Mocked<AiQuizGenerationService>;

        mockModal = {
            close: jest.fn(),
            dismiss: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [AiQuizGenerationModalComponent],
            providers: [
                TranslateStore,
                {
                    provide: TranslateService,
                    useValue: {
                        instant: (key: string) => key,
                        get: () => ({ subscribe: () => {} }),
                    },
                },
                { provide: AiQuizGenerationService, useValue: mockService },
                { provide: NgbActiveModal, useValue: mockModal },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(AiQuizGenerationModalComponent);
        comp = fixture.componentInstance;
        // the dialog always needs a courseId
        comp.courseId = 99;
    });

    it('should initialize with default form values', () => {
        expect(comp.formData.language).toBe(AiLanguage.ENGLISH);
        expect(comp.formData.difficultyLevel).toBe(AiDifficultyLevel.MEDIUM);
        expect(comp.formData.requestedSubtype).toBe(AiRequestedSubtype.SINGLE_CORRECT);
        expect(comp.loading()).toBeFalse();
    });

    it('should NOT call service.generate when form is invalid', () => {
        comp.generate({ valid: false } as any);
        expect(mockService.generate).not.toHaveBeenCalled();
    });

    it('should call service.generate and set questions/warnings', () => {
        const mockResponse: AiQuizGenerationResponse = {
            questions: [
                {
                    title: 'Q1',
                    text: 'What is Java?',
                    subtype: AiRequestedSubtype.SINGLE_CORRECT,
                    tags: [],
                    competencyIds: [],
                    options: [],
                },
            ],
            warnings: ['Be careful!'],
        };
        mockService.generate.mockReturnValue(of(mockResponse));

        comp.generate({ valid: true } as any);

        expect(mockService.generate).toHaveBeenCalledWith(99, comp.formData);
        expect(comp.generated()).toHaveLength(1);
        expect(comp.warnings()[0]).toContain('Be careful');
        // preselected
        expect(comp.anySelected).toBeTrue();
    });

    it('should dismiss modal when cancel() is called', () => {
        comp.cancel();
        expect(mockModal.dismiss).toHaveBeenCalledOnce();
    });

    it('should close modal and return selected questions in useInEditor()', () => {
        comp.generated.set([
            {
                title: 'A',
                text: 'B',
                subtype: AiRequestedSubtype.SINGLE_CORRECT,
                tags: [],
                competencyIds: [],
                options: [],
            },
        ]);
        comp.selected[0] = true;

        comp.useInEditor();

        expect(mockModal.close).toHaveBeenCalledOnce();
        const arg = mockModal.close.mock.calls[0][0];
        expect(arg.questions).toHaveLength(1);
        expect(arg.requestedSubtype).toBe(AiRequestedSubtype.SINGLE_CORRECT);
        expect(arg.requestedDifficulty).toBe(AiDifficultyLevel.MEDIUM);
    });

    it('should not close modal if nothing selected', () => {
        comp.generated.set([
            {
                title: 'A',
                text: 'B',
                subtype: AiRequestedSubtype.SINGLE_CORRECT,
                tags: [],
                competencyIds: [],
                options: [],
            },
        ]);
        comp.selected[0] = false;

        comp.useInEditor();

        expect(mockModal.close).not.toHaveBeenCalled();
    });

    it('should convert difficulty slider correctly', () => {
        expect(comp.difficultyToSlider(AiDifficultyLevel.EASY)).toBe(0);
        expect(comp.difficultyToSlider(AiDifficultyLevel.MEDIUM)).toBe(1);
        expect(comp.difficultyToSlider(AiDifficultyLevel.HARD)).toBe(2);

        expect(comp.sliderToDifficulty('0')).toBe(AiDifficultyLevel.EASY);
        expect(comp.sliderToDifficulty('1')).toBe(AiDifficultyLevel.MEDIUM);
        expect(comp.sliderToDifficulty('2')).toBe(AiDifficultyLevel.HARD);
    });
});

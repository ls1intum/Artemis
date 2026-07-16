import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MockPipe } from 'ng-mocks';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseVariantAiModalWizardComponent } from 'app/course/manage/exercises/create-variant-modal/exercise-variant-ai-modal-wizard.component';
import { ExerciseVariantGenerationService } from 'app/hyperion/services/exercise-variant-generation.service';
import { ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DifficultyLevel, Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { VariantGenerationRequest } from 'app/openapi/model/variantGenerationRequest';

/**
 * Vitest specs for the exam path of the AI variant wizard: exam exercises must place the variant into the
 * source's exam exercise group automatically (SAME_EXAM_GROUP, no placement step) and must NOT offer difficulty
 * adaptation — a per-student variant with a different difficulty would be unfair.
 */
describe('ExerciseVariantAiModalWizardComponent (exam path)', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVariantAiModalWizardComponent>;
    let component: ExerciseVariantAiModalWizardComponent;
    let generationServiceMock: {
        startGeneration: ReturnType<typeof vi.fn>;
        jobEvents: ReturnType<typeof vi.fn>;
        getJobDetail: ReturnType<typeof vi.fn>;
        cancelJob: ReturnType<typeof vi.fn>;
    };
    let groupServiceMock: { getGroupsForCourse: ReturnType<typeof vi.fn> };

    const examExercise: Exercise = { id: 55, title: 'Sorting: Exam Edition', type: ExerciseType.PROGRAMMING, difficulty: DifficultyLevel.HARD } as Exercise;

    beforeEach(async () => {
        generationServiceMock = {
            startGeneration: vi.fn().mockReturnValue(of('job-exam')),
            jobEvents: vi.fn().mockReturnValue(of()),
            getJobDetail: vi.fn().mockReturnValue(of({ job: undefined, stepOutputs: {}, request: undefined })),
            cancelJob: vi.fn().mockReturnValue(of(undefined)),
        };
        groupServiceMock = { getGroupsForCourse: vi.fn().mockReturnValue(of([])) };

        await TestBed.configureTestingModule({
            imports: [ExerciseVariantAiModalWizardComponent],
            providers: [
                { provide: ExerciseVariantGenerationService, useValue: generationServiceMock },
                { provide: ExerciseVariantGroupService, useValue: groupServiceMock },
                { provide: ExerciseService, useValue: { find: vi.fn().mockReturnValue(of({ body: undefined })) } },
                {
                    provide: TranslateService,
                    useValue: { instant: (key: string) => key, get: (key: string) => of(key), onLangChange: of(), onTranslationChange: of(), onDefaultLangChange: of() },
                },
            ],
        })
            .overrideComponent(ExerciseVariantAiModalWizardComponent, {
                remove: { imports: [ArtemisTranslatePipe] },
                add: { imports: [MockPipe(ArtemisTranslatePipe, (key) => key)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseVariantAiModalWizardComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('sourceExercise', examExercise);
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('examExercise', true);
        fixture.componentRef.setInput('visible', true);
    });

    it('detects the exam context from the explicit input even without a nested exerciseGroup', () => {
        fixture.detectChanges();
        expect(component.isExamExercise()).toBe(true);
        // Exam context must not trigger a course variant-group lookup — placement is forced, not chosen.
        expect(groupServiceMock.getGroupsForCourse).not.toHaveBeenCalled();
    });

    it('hides the difficulty adaptation option for exam exercises', () => {
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="variant-option-difficulty"]')).toBeNull();
    });

    it('skips the placement step and starts generation with SAME_EXAM_GROUP', () => {
        fixture.detectChanges();
        component.changeDomain.set(true);
        component.domainText.set('space exploration');

        component.goToPlacement();

        // Never lands on the placement step (3) — jumps straight to the running view (4).
        expect(component.wizardStep()).toBe(4);
        expect(generationServiceMock.startGeneration).toHaveBeenCalledTimes(1);
        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.placement).toEqual({ type: 'SAME_EXAM_GROUP' });
    });

    it('never sends a target difficulty for an exam exercise even if the flag is somehow set', () => {
        fixture.detectChanges();
        // Defensive: the option is hidden, but a stale flag must not leak into the request.
        component.changeDifficulty.set(true);
        component.targetDifficulty.set(DifficultyLevel.EASY);
        component.changeDomain.set(true);
        component.domainText.set('banking');

        component.startGeneration();

        const request = generationServiceMock.startGeneration.mock.calls[0][1] as VariantGenerationRequest;
        expect(request.targetDifficulty).toBeUndefined();
        expect(request.domainText).toBe('banking');
    });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseCompetency } from 'app/atlas/shared/entities/competency.model';
import { CourseCompetencyApiService } from 'app/atlas/shared/services/course-competency-api.service';
import { provideArtemisTumAetUiTranslator } from 'app/shared-ui/tum-aet-ui-integration/artemis-tumaet-ui-translator';
import { QuizAiGenerationModalComponent } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation-modal.component';
import { QuizAiGenerationService } from 'app/hyperion/quiz/quiz-ai-generation-modal/quiz-ai-generation.service';

describe('QuizAiGenerationModalComponent', () => {
    let fixture: ComponentFixture<QuizAiGenerationModalComponent>;
    let component: QuizAiGenerationModalComponent;

    const competency = (id: number, title: string) => ({ id, title }) as CourseCompetency;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [QuizAiGenerationModalComponent],
            providers: [
                provideTranslateService(),
                provideArtemisTumAetUiTranslator(),
                { provide: AlertService, useValue: { error: vi.fn() } },
                { provide: QuizAiGenerationService, useValue: { generateQuestions: vi.fn() } },
                { provide: CourseCompetencyApiService, useValue: { getCourseCompetenciesByCourseId: vi.fn().mockResolvedValue([]) } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizAiGenerationModalComponent);
        fixture.componentRef.setInput('visible', false);
        fixture.componentRef.setInput('courseId', 1);
        fixture.detectChanges();
        component = fixture.componentInstance;
    });

    describe('competency selection', () => {
        beforeEach(() => {
            component.courseCompetencies.set([competency(1, 'Recursion'), competency(2, 'Sorting'), competency(3, 'Graphs')]);
        });

        it('selects the competencies of the chosen ids and ignores unknown ids', () => {
            component.onCompetencySelectionChange([3, 1, 99]);

            expect(component.selectedCompetencies().map((selected) => selected.id)).toEqual([1, 3]);
            expect(component.selectedCompetencyIds()).toEqual([1, 3]);
        });

        it('clears the selection when no id is chosen', () => {
            component.onCompetencySelectionChange([2]);
            expect(component.selectedCompetencyIds()).toEqual([2]);

            component.onCompetencySelectionChange([]);

            expect(component.selectedCompetencies()).toEqual([]);
            expect(component.selectedCompetencyIds()).toEqual([]);
        });

        it('keeps the selection by id when the competency list is loaded again', () => {
            component.onCompetencySelectionChange([2]);

            // a reload delivers new objects for the same competencies
            component.courseCompetencies.set([competency(1, 'Recursion'), competency(2, 'Sorting'), competency(3, 'Graphs')]);

            expect(component.selectedCompetencyIds()).toEqual([2]);
        });

        it('can only generate in competency mode once a competency is selected', () => {
            component.selectMode('competency-graph');
            expect(component.canGenerate()).toBe(false);

            component.onCompetencySelectionChange([1]);

            expect(component.canGenerate()).toBe(true);
        });
    });
});

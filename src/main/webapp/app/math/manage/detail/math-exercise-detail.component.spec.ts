import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathExerciseDetailComponent } from 'app/math/manage/detail/math-exercise-detail.component';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { MathSubmissionService } from 'app/math/participate/service/math-submission.service';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';

describe('MathExerciseDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathExerciseDetailComponent;

    beforeEach(() => {
        const exercise = new MathExercise(undefined);
        exercise.id = 1;
        exercise.title = 'Test';
        exercise.description = 'desc';

        TestBed.configureTestingModule({
            imports: [MathExerciseDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathExerciseService, { find: () => of({ body: exercise }) as any }),
                MockProvider(MathSubmissionService, { getSubmittedSubmissions: () => of([]) }),
                MockProvider(StatisticsService, { getExerciseStatistics: () => of({}) as any }),
                MockProvider(ArtemisMarkdownService, { safeHtmlForMarkdown: () => '' as any }),
                MockProvider(EventManager, { subscribe: vi.fn(), destroy: vi.fn(), broadcast: vi.fn() }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                { provide: ActivatedRoute, useValue: { data: of({ mathExercise: exercise }), params: of({}), snapshot: { params: {} } } },
            ],
        }).overrideComponent(MathExerciseDetailComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathExerciseDetailComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('loads the exercise from the resolver data', () => {
        expect(component.mathExercise).toBeTruthy();
        expect(component.mathExercise.id).toBe(1);
    });

    it('produces exercise detail sections', () => {
        const sections = component.getExerciseDetailSections();
        expect(sections.length).toBeGreaterThan(0);
    });
});

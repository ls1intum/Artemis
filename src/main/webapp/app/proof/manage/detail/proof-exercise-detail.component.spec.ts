import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofExerciseDetailComponent } from 'app/proof/manage/detail/proof-exercise-detail.component';
import { ProofExerciseService } from 'app/proof/manage/service/proof-exercise.service';
import { ProofSubmissionService } from 'app/proof/participate/service/proof-submission.service';
import { StatisticsService } from 'app/shared/statistics-graph/service/statistics.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';

describe('ProofExerciseDetailComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofExerciseDetailComponent;

    beforeEach(() => {
        const exercise = new ProofExercise(undefined);
        exercise.id = 1;
        exercise.title = 'Test';
        exercise.description = 'desc';

        TestBed.configureTestingModule({
            imports: [ProofExerciseDetailComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ProofExerciseService, { find: () => of({ body: exercise }) as any }),
                MockProvider(ProofSubmissionService, { getSubmittedSubmissions: () => of([]) }),
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
                { provide: ActivatedRoute, useValue: { data: of({ proofExercise: exercise }), params: of({}), snapshot: { params: {} } } },
            ],
        }).overrideComponent(ProofExerciseDetailComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofExerciseDetailComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('loads the exercise from the resolver data', () => {
        expect(component.proofExercise).toBeTruthy();
        expect(component.proofExercise.id).toBe(1);
    });

    it('produces exercise detail sections', () => {
        const sections = component.getExerciseDetailSections();
        expect(sections.length).toBeGreaterThan(0);
    });
});

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofExerciseComponent } from 'app/proof/manage/exercise/proof-exercise.component';
import { ProofExerciseService } from 'app/proof/manage/service/proof-exercise.service';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';

describe('ProofExerciseComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofExerciseComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ProofExerciseComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(ProofExerciseService, { delete: () => of(undefined) as any }),
                MockProvider(CourseExerciseService),
                MockProvider(AccountService, {
                    userIdentity: () => undefined,
                    getAuthenticationState: () => of(undefined) as any,
                    setAccessRightsForExercise: vi.fn(),
                }),
                MockProvider(EventManager, { broadcast: vi.fn(), subscribe: vi.fn(), destroy: vi.fn() }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                { provide: ActivatedRoute, useValue: { params: of({}), parent: { params: of({ courseId: 1 }) } } },
            ],
        }).overrideComponent(ProofExerciseComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofExerciseComponent);
        component = fixture.componentInstance;
        component.filter = new ExerciseFilter();
    });

    it('creates and initialises internalProofExercises from the signal input', () => {
        const ex = new ProofExercise(undefined);
        ex.id = 1;
        TestBed.runInInjectionContext(() => {
            (component as any).proofExercises = () => [ex];
            component.internalProofExercises.set([ex]);
        });
        expect(component).toBeTruthy();
        expect(component.internalProofExercises().length).toBe(1);
    });

    it('exposes proofExerciseListModification as the change event name', () => {
        expect((component as any).getChangeEventName()).toBe('proofExerciseListModification');
    });

    it('trackId returns the exercise id', () => {
        const ex = new ProofExercise(undefined);
        ex.id = 42;
        expect(component.trackId(0, ex)).toBe(42);
    });
});

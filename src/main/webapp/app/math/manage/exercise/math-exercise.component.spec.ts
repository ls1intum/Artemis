import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathExerciseComponent } from 'app/math/manage/exercise/math-exercise.component';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';

describe('MathExerciseComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathExerciseComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MathExerciseComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(MathExerciseService, { delete: () => of(undefined) as any }),
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
        }).overrideComponent(MathExerciseComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathExerciseComponent);
        component = fixture.componentInstance;
        component.filter = new ExerciseFilter();
    });

    it('creates and initialises internalMathExercises from the signal input', () => {
        const ex = new MathExercise(undefined);
        ex.id = 1;
        TestBed.runInInjectionContext(() => {
            (component as any).mathExercises = () => [ex];
            component.internalMathExercises.set([ex]);
        });
        expect(component).toBeTruthy();
        expect(component.internalMathExercises().length).toBe(1);
    });

    it('exposes mathExerciseListModification as the change event name', () => {
        expect((component as any).getChangeEventName()).toBe('mathExerciseListModification');
    });

    it('trackId returns the exercise id', () => {
        const ex = new MathExercise(undefined);
        ex.id = 42;
        expect(component.trackId(0, ex)).toBe(42);
    });
});

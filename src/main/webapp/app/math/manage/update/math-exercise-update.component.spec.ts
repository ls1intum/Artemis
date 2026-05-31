import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { MathExerciseUpdateComponent } from 'app/math/manage/update/math-exercise-update.component';
import { MathExerciseService } from 'app/math/manage/service/math-exercise.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';

describe('MathExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: MathExerciseUpdateComponent;
    let mathExerciseService: { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn>; verifyReachability: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        mathExerciseService = {
            create: vi.fn().mockReturnValue(of({ body: new MathExercise(undefined) })),
            update: vi.fn().mockReturnValue(of({ body: new MathExercise(undefined) })),
            verifyReachability: vi.fn().mockReturnValue(of(undefined)),
        };
        router = { navigate: vi.fn() };
        const exercise = new MathExercise(undefined);

        TestBed.configureTestingModule({
            imports: [MathExerciseUpdateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: MathExerciseService, useValue: mathExerciseService },
                { provide: Router, useValue: router },
                MockProvider(ExerciseService, { validateDate: vi.fn() }),
                MockProvider(ProfileService, { isDevelopment: () => false }),
                MockProvider(TranslateService, {
                    instant: (k: string) => k,
                    get: (k: string) => of(k) as any,
                    onLangChange: of() as any,
                    onTranslationChange: of() as any,
                    onDefaultLangChange: of() as any,
                }),
                { provide: ActivatedRoute, useValue: { data: of({ mathExercise: exercise }), snapshot: { params: { courseId: 7 } } } },
            ],
        }).overrideComponent(MathExerciseUpdateComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(MathExerciseUpdateComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('initialises with exampleDerivations defaulted to an empty array', () => {
        expect(component.mathExercise).toBeTruthy();
        expect(component.mathExercise.exampleDerivations).toEqual([]);
        expect(component.isSaving).toBe(false);
    });

    it('addExampleDerivation pushes a new empty step list', () => {
        component.addExampleDerivation();
        expect(component.mathExercise.exampleDerivations).toHaveLength(1);
    });

    it('removeExampleDerivation drops the entry at the given index', () => {
        component.addExampleDerivation();
        component.addExampleDerivation();
        component.removeExampleDerivation(0);
        expect(component.mathExercise.exampleDerivations).toHaveLength(1);
    });

    it('save() routes through create when the exercise has no id', () => {
        component.save();
        expect(mathExerciseService.create).toHaveBeenCalled();
    });

    it('save() routes through update when the exercise has an id', () => {
        component.mathExercise.id = 5;
        component.save();
        expect(mathExerciseService.update).toHaveBeenCalled();
    });

    it('checkReachability sets the saveFirst i18n key when the exercise is unsaved', () => {
        component.checkReachability();
        expect(component.reachabilityError()).toBe('artemisApp.mathExercise.reachability.saveFirst');
    });
});

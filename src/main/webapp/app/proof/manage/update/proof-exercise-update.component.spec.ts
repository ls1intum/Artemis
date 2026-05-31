import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { ProofExerciseUpdateComponent } from 'app/proof/manage/update/proof-exercise-update.component';
import { ProofExerciseService } from 'app/proof/manage/service/proof-exercise.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProofExercise } from 'app/proof/shared/entities/proof-exercise.model';

describe('ProofExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ProofExerciseUpdateComponent;
    let proofExerciseService: { create: ReturnType<typeof vi.fn>; update: ReturnType<typeof vi.fn>; verifyReachability: ReturnType<typeof vi.fn> };
    let router: { navigate: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        proofExerciseService = {
            create: vi.fn().mockReturnValue(of({ body: new ProofExercise(undefined) })),
            update: vi.fn().mockReturnValue(of({ body: new ProofExercise(undefined) })),
            verifyReachability: vi.fn().mockReturnValue(of(undefined)),
        };
        router = { navigate: vi.fn() };
        const exercise = new ProofExercise(undefined);

        TestBed.configureTestingModule({
            imports: [ProofExerciseUpdateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: ProofExerciseService, useValue: proofExerciseService },
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
                { provide: ActivatedRoute, useValue: { data: of({ proofExercise: exercise }), snapshot: { params: { courseId: 7 } } } },
            ],
        }).overrideComponent(ProofExerciseUpdateComponent, { set: { imports: [], template: '' } });

        const fixture = TestBed.createComponent(ProofExerciseUpdateComponent);
        component = fixture.componentInstance;
        component.ngOnInit();
    });

    it('initialises with exampleDerivations defaulted to an empty array', () => {
        expect(component.proofExercise).toBeTruthy();
        expect(component.proofExercise.exampleDerivations).toEqual([]);
        expect(component.isSaving).toBe(false);
    });

    it('addExampleDerivation pushes a new empty step list', () => {
        component.addExampleDerivation();
        expect(component.proofExercise.exampleDerivations).toHaveLength(1);
    });

    it('removeExampleDerivation drops the entry at the given index', () => {
        component.addExampleDerivation();
        component.addExampleDerivation();
        component.removeExampleDerivation(0);
        expect(component.proofExercise.exampleDerivations).toHaveLength(1);
    });

    it('save() routes through create when the exercise has no id', () => {
        component.save();
        expect(proofExerciseService.create).toHaveBeenCalled();
    });

    it('save() routes through update when the exercise has an id', () => {
        component.proofExercise.id = 5;
        component.save();
        expect(proofExerciseService.update).toHaveBeenCalled();
    });

    it('checkReachability sets the saveFirst i18n key when the exercise is unsaved', () => {
        component.checkReachability();
        expect(component.reachabilityError()).toBe('artemisApp.proofExercise.reachability.saveFirst');
    });
});

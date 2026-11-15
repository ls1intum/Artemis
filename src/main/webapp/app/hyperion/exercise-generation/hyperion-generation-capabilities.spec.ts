import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HyperionExerciseGenerationApi } from 'app/openapi/api/hyperion-exercise-generation-api';
import { ExerciseGenerationCapabilities } from 'app/openapi/model/exercise-generation-capabilities';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { generationCapabilityBlocker, injectGenerationCapabilities } from './hyperion-generation-capabilities';

describe('Shared programming authoring capabilities', () => {
    const exercise = signal<Exercise | undefined>(undefined, { equal: () => false });
    const authorized = signal(true);
    const enabled = signal(true);
    const describe = vi.fn();
    const supported = () =>
        Object.assign(new ProgrammingExercise(undefined, undefined), { id: 12, programmingLanguage: ProgrammingLanguage.JAVA, projectType: ProjectType.PLAIN_GRADLE });

    beforeEach(() => {
        exercise.set(supported());
        authorized.set(true);
        enabled.set(true);
        describe.mockReset().mockReturnValue(of({ supported: true, canCreateVariant: true, canAdapt: true }));
        TestBed.configureTestingModule({
            providers: [
                { provide: ProfileService, useValue: { isModuleFeatureActive: () => enabled() } },
                { provide: HyperionExerciseGenerationApi, useValue: { getGenerationCapabilities: describe } },
            ],
        });
    });

    afterEach(() => TestBed.resetTestingModule());

    it('uses the server lifecycle policy, so a released source can allow cloning but not adaptation', () => {
        describe.mockReturnValue(of({ supported: true, canCreateVariant: true, canAdapt: false, restriction: 'exerciseAlreadyReleased' }));
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        expect(describe).toHaveBeenCalledWith(12);
        expect(capabilities.value()?.canCreateVariant).toBe(true);
        expect(generationCapabilityBlocker(capabilities.value())).toMatch(/released$/);
    });

    it.each(['quiz', 'unsupported', 'exam', 'unauthorized', 'disabled'])('does not request capabilities for %s', (reason) => {
        if (reason === 'exam') exercise.set(Object.assign(supported(), { exerciseGroup: { id: 4 } }));
        if (reason === 'quiz') exercise.set({ id: 12, type: ExerciseType.QUIZ } as Exercise);
        if (reason === 'unsupported') exercise.set(Object.assign(supported(), { projectType: ProjectType.MAVEN_BLACKBOX }));
        if (reason === 'unauthorized') authorized.set(false);
        if (reason === 'disabled') enabled.set(false);
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        expect(describe).not.toHaveBeenCalled();
        expect(capabilities.value()).toBeUndefined();
    });

    it('keeps a pending request when statement edits republish the same exercise or a fresh object', async () => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        TestBed.overrideProvider(HyperionExerciseGenerationApi, { useFactory: () => new HyperionExerciseGenerationApi() });
        const http = TestBed.inject(HttpTestingController);
        const url = '/api/hyperion/programming-exercises/12/generation/capabilities';
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        const pending = http.expectOne(url);
        expect(pending.request.method).toBe('GET');

        for (const statement of ['First edit', 'Second edit', 'Third edit']) {
            const current = exercise()!;
            current.problemStatement = statement;
            exercise.set(current);
            TestBed.tick();
            exercise.set(Object.assign(supported(), { problemStatement: statement }));
            TestBed.tick();
        }

        http.expectNone(url);
        expect(capabilities.isLoading()).toBe(true);
        expect(pending.cancelled).toBe(false);
        pending.flush({ canAdapt: true });
        await Promise.resolve();
        TestBed.tick();
        expect(capabilities.value()?.canAdapt).toBe(true);
        http.verify();
    });

    it.each(['configuration', 'authorization', 'module feature'])('requests current capabilities again when %s eligibility returns', (reason) => {
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        expect(describe).toHaveBeenCalledExactlyOnceWith(12);
        if (reason === 'configuration') exercise.set(Object.assign(supported(), { staticCodeAnalysisEnabled: true }));
        if (reason === 'authorization') authorized.set(false);
        if (reason === 'module feature') enabled.set(false);
        TestBed.tick();
        expect(capabilities.value()).toBeUndefined();
        expect(describe).toHaveBeenCalledTimes(1);
        exercise.set(supported());
        authorized.set(true);
        enabled.set(true);
        TestBed.tick();
        expect(describe).toHaveBeenCalledTimes(2);
        expect(describe).toHaveBeenLastCalledWith(12);
    });

    it('discards a late response for the previous exercise', () => {
        const previous = new Subject<ExerciseGenerationCapabilities>();
        describe.mockReturnValueOnce(previous).mockReturnValueOnce(of({ supported: true, canAdapt: false, restriction: 'examAlreadyAssigned' }));
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        exercise.set(Object.assign(supported(), { id: 14 }));
        TestBed.tick();
        expect(describe).toHaveBeenCalledTimes(2);
        expect(describe).toHaveBeenLastCalledWith(14);
        previous.next({ canAdapt: true });
        expect(capabilities.value()?.canAdapt).toBe(false);
        expect(generationCapabilityBlocker(capabilities.value())).toMatch(/preparationOnly$/);
    });

    it.each([
        ['generationHarnessUnsupported', 'harnessUnsupported'],
        ['generationHarnessUnavailable', 'harnessUnavailable'],
    ])('explains the server harness restriction %s', (restriction, reason) => {
        expect(generationCapabilityBlocker({ supported: false, canAdapt: false, restriction })).toBe('artemisApp.hyperion.generation.blocker.' + reason);
    });

    it('fails closed on a denied or unavailable capability response', () => {
        describe.mockReturnValue(throwError(() => new Error('forbidden')));
        const capabilities = TestBed.runInInjectionContext(() => injectGenerationCapabilities(exercise, authorized));
        TestBed.tick();
        expect(capabilities.value()).toBeUndefined();
        expect(generationCapabilityBlocker(capabilities.value())).toMatch(/capabilitiesUnavailable$/);
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionBriefDialogComponent } from 'app/hyperion/exercise-generation/create/hyperion-brief-dialog.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

const COURSE_ID = 7;
const TITLE_SUGGESTION_DEBOUNCE_MS = 800;
const CREATED_EXERCISE = { id: 42, title: 'AI draft exercise' } as ProgrammingExercise;

describe('HyperionBriefDialogComponent', () => {
    let fixture: ComponentFixture<HyperionBriefDialogComponent>;
    let component: HyperionBriefDialogComponent;
    let programmingExerciseService: ProgrammingExerciseService;
    let generationService: HyperionExerciseGenerationService;
    let registry: { track: ReturnType<typeof vi.fn>; markSeen: ReturnType<typeof vi.fn> };
    let navigateSpy: ReturnType<typeof vi.spyOn>;
    let suggestTitleSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        registry = { track: vi.fn(), markSeen: vi.fn() };
        await TestBed.configureTestingModule({
            imports: [HyperionBriefDialogComponent],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: HyperionJobRegistryService, useValue: registry },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(HyperionBriefDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', COURSE_ID);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        generationService = TestBed.inject(HyperionExerciseGenerationService);
        navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
        suggestTitleSpy = vi.spyOn(generationService, 'suggestTitle').mockReturnValue(of({ title: 'Bounded Stack' }));
        component.visible.set(true);
        fixture.detectChanges();
    });

    function setupSucceeds(): void {
        vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: CREATED_EXERCISE })));
    }

    describe('brief validation', () => {
        it('refuses a brief that is too short and accepts one at exactly the minimum', () => {
            component.brief.set('a'.repeat(39));
            expect(component.canGenerate()).toBe(false);

            component.brief.set('a'.repeat(40));
            expect(component.canGenerate()).toBe(true);
        });

        it('accepts a brief at exactly the maximum and refuses one beyond it', () => {
            component.brief.set('a'.repeat(8000));
            expect(component.canGenerate()).toBe(true);

            component.brief.set('a'.repeat(8001));
            expect(component.canGenerate()).toBe(false);
        });

        it('measures the trimmed brief, so whitespace cannot pass for detail', () => {
            component.brief.set(`${' '.repeat(50)}${'a'.repeat(39)}${' '.repeat(50)}`);
            expect(component.canGenerate()).toBe(false);
        });

        it('shows the length error only once the field has been left', () => {
            component.brief.set('too short');
            fixture.detectChanges();
            expect(document.body.querySelector('[data-testid="hyperion-brief-error"]')).toBeNull();

            component.briefTouched.set(true);
            fixture.detectChanges();
            expect(document.body.querySelector('[data-testid="hyperion-brief-error"]')).not.toBeNull();
        });
    });

    /** Real time rather than fake timers: Angular's effect scheduler does not advance with them, so the signal never reaches the debounce. */
    function afterTheDebounce(): Promise<void> {
        return new Promise((resolve) => setTimeout(resolve, TITLE_SUGGESTION_DEBOUNCE_MS + 200));
    }

    describe('the suggested title', () => {
        it('asks once the instructor pauses rather than on every keystroke', async () => {
            component.brief.set('a'.repeat(60));
            TestBed.tick();
            component.brief.set('a'.repeat(61));
            TestBed.tick();

            // Two changes have been made and nothing has been asked for yet, which is the whole point of the debounce.
            expect(suggestTitleSpy).not.toHaveBeenCalled();

            await afterTheDebounce();

            expect(suggestTitleSpy).toHaveBeenCalledOnce();
            expect(suggestTitleSpy).toHaveBeenCalledWith(COURSE_ID, 'a'.repeat(61));
            expect(component.title()).toBe('Bounded Stack');
        });

        it('never asks again once the instructor has titled the exercise themselves', async () => {
            component.editTitle('Ring Buffer');

            component.brief.set('a'.repeat(60));
            TestBed.tick();
            await afterTheDebounce();

            expect(suggestTitleSpy).not.toHaveBeenCalled();
            expect(component.title()).toBe('Ring Buffer');
        });

        it('overwrites the instructor edit only when they ask for another suggestion', () => {
            component.brief.set('a'.repeat(60));
            component.editTitle('Ring Buffer');

            component.regenerateTitle();

            expect(suggestTitleSpy).toHaveBeenCalledWith(COURSE_ID, 'a'.repeat(60));
            expect(component.title()).toBe('Bounded Stack');
        });

        it('leaves the fallback in the field and Generate usable when the request fails', () => {
            suggestTitleSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            // The real translation rather than the mock's key, because the fallback has to be a title Artemis itself accepts.
            vi.spyOn(TestBed.inject(TranslateService), 'instant').mockReturnValue('AI draft exercise');
            component.brief.set('a'.repeat(60));

            component.regenerateTitle();

            expect(component.title()).toBe('AI draft exercise');
            expect(component.suggestingTitle()).toBe(false);
            expect(component.canGenerate()).toBe(true);
        });

        it('refuses a title Artemis would reject and says so inline', () => {
            component.brief.set('a'.repeat(60));
            component.editTitle('Stack: bounded');
            component.titleTouched.set(true);
            fixture.detectChanges();

            expect(component.canGenerate()).toBe(false);
            expect(document.body.querySelector('[data-testid="hyperion-title-error"]')).not.toBeNull();
        });

        it('hides the field until the brief is worth naming', () => {
            component.brief.set('too short');
            fixture.detectChanges();
            expect(document.body.querySelector('[data-testid="hyperion-title-section"]')).toBeNull();

            component.brief.set('a'.repeat(60));
            fixture.detectChanges();
            expect(document.body.querySelector('[data-testid="hyperion-title-section"]')).not.toBeNull();
        });
    });

    describe('starting a run', () => {
        beforeEach(() => {
            component.brief.set('a'.repeat(60));
        });

        it('creates the draft, starts the run, tracks it and opens the run URL', () => {
            setupSucceeds();
            const generateSpy = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));

            component.generate();

            expect(generateSpy).toHaveBeenCalledWith(42, { mode: 'GENERATE', prompt: 'a'.repeat(60) });
            expect(registry.track).toHaveBeenCalledWith({ jobId: 'job-1', exerciseId: 42, courseId: COURSE_ID, exerciseTitle: 'AI draft exercise', mode: 'GENERATE' });
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
            expect(component.visible()).toBe(false);
        });

        it('titles the draft with whatever the title field holds', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: CREATED_EXERCISE })));
            vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
            component.editTitle('Bounded Stack');

            component.generate();

            expect(setupSpy.mock.calls[0][0].title).toBe('Bounded Stack');
        });

        it('falls back to the translated draft title while no suggestion has arrived yet', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: CREATED_EXERCISE })));
            vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));

            component.generate();

            expect(setupSpy.mock.calls[0][0].title).toBe('artemisApp.hyperion.generation.brief.draftTitle');
        });

        it('keeps the dialog and the brief when the draft could not be created', () => {
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            component.generate();

            expect(component.setupFailed()).toBe(true);
            expect(component.brief()).toBe('a'.repeat(60));
            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('a start that fails after the draft was created', () => {
        beforeEach(() => {
            component.brief.set('a'.repeat(60));
            setupSucceeds();
            vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            component.generate();
            fixture.detectChanges();
        });

        it('offers both a retry and a delete for the exercise it left behind', () => {
            expect(component.createdExercise()?.id).toBe(42);
            expect(document.body.querySelector('[data-testid="hyperion-brief-start-retry"]')).not.toBeNull();
            expect(document.body.querySelector('[data-testid="hyperion-brief-start-delete"]')).not.toBeNull();
        });

        it('retries without provisioning a second exercise', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            setupSpy.mockClear();
            const generateSpy = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-2' }));

            component.retryStart();

            expect(setupSpy).not.toHaveBeenCalled();
            expect(generateSpy).toHaveBeenCalledWith(42, { mode: 'GENERATE', prompt: 'a'.repeat(60) });
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
        });

        it('deletes the draft exercise through the exercise service and resets', () => {
            const deleteSpy = vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

            component.deleteCreatedExercise();

            expect(deleteSpy).toHaveBeenCalledWith(42, false, false);
            expect(component.createdExercise()).toBeUndefined();
            expect(component.brief()).toBe('');
        });

        it('says so when the draft could not be deleted, and keeps it selectable for another attempt', () => {
            vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            component.deleteCreatedExercise();

            expect(component.deleteFailed()).toBe(true);
            expect(component.createdExercise()?.id).toBe(42);
        });
    });

    it('makes retrying the primary action when the server had no generation capacity', () => {
        component.brief.set('a'.repeat(60));
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorKey: 'generationCapacityUnavailable' } })));

        component.generate();
        fixture.detectChanges();

        const banner = document.body.querySelector('[data-testid="hyperion-brief-start-failed"]');
        expect(banner?.textContent).toContain('error.generationCapacityUnavailable');
        // Nothing was created, so trying again is the answer; the solid primary button is how that is said.
        expect(document.body.querySelector('[data-testid="hyperion-brief-start-retry"] button')?.className).toContain('tum:bg-primary');
    });
});

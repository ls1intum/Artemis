import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionBriefDialogComponent } from 'app/hyperion/exercise-generation/create/hyperion-brief-dialog.component';
import { HyperionExerciseGenerationService } from 'app/hyperion/exercise-generation/hyperion-exercise-generation.service';
import { HyperionJobRegistryService } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { DifficultyLevel } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HyperionMetadataSuggestion } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

const COURSE_ID = 7;
const BRIEF = 'a'.repeat(60);
const CREATED_EXERCISE = { id: 42, title: 'AI draft exercise' } as ProgrammingExercise;
const SUGGESTION: HyperionMetadataSuggestion = {
    title: 'Bounded Stack',
    shortName: 'boundedstack',
    packageName: 'de.tum.cit.aet.boundedstack',
    difficulty: 'EASY',
    maxPoints: 10,
};

describe('HyperionBriefDialogComponent', () => {
    let fixture: ComponentFixture<HyperionBriefDialogComponent>;
    let component: HyperionBriefDialogComponent;
    let programmingExerciseService: ProgrammingExerciseService;
    let generationService: HyperionExerciseGenerationService;
    let registry: { track: ReturnType<typeof vi.fn>; markSeen: ReturnType<typeof vi.fn> };
    let navigateSpy: ReturnType<typeof vi.spyOn>;
    let suggestMetadataSpy: ReturnType<typeof vi.spyOn>;

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
        suggestMetadataSpy = vi.spyOn(generationService, 'suggestMetadata').mockReturnValue(of(SUGGESTION));
        component.visible.set(true);
        fixture.detectChanges();
    });

    function setupSucceeds(): void {
        vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: CREATED_EXERCISE })));
    }

    /** Everything Generate needs, typed rather than suggested, so a test can be about one field at a time. */
    function fillMetadataByHand(): void {
        component.editTitle('Ring Buffer');
        component.editShortName('ringbuffer');
        component.editPackageName('de.tum.cit.aet.ringbuffer');
        component.editMaxPoints(10);
    }

    function query(testId: string): Element | null {
        return document.body.querySelector(`[data-testid="${testId}"]`);
    }

    describe('brief validation', () => {
        beforeEach(() => fillMetadataByHand());

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
            expect(query('hyperion-brief-error')).toBeNull();

            component.briefTouched.set(true);
            fixture.detectChanges();
            expect(query('hyperion-brief-error')).not.toBeNull();
        });
    });

    describe('asking for metadata', () => {
        /** Real time rather than fake timers: a debounce, if one came back, would be scheduled on the real clock. */
        function afterAPauseInTyping(): Promise<void> {
            return new Promise((resolve) => setTimeout(resolve, 1000));
        }

        it('asks for nothing at all while the instructor types', async () => {
            component.brief.set(BRIEF);
            TestBed.tick();
            component.brief.set(`${BRIEF}b`);
            TestBed.tick();

            await afterAPauseInTyping();

            expect(suggestMetadataSpy).not.toHaveBeenCalled();
            expect(component.title()).toBe('');
            expect(component.shortName()).toBe('');
            expect(component.packageName()).toBe('');
            expect(component.maxPoints()).toBeUndefined();
        });

        it('issues exactly one request when the button is pressed, and fills every field from it', () => {
            component.brief.set(BRIEF);

            component.suggestMetadata();

            expect(suggestMetadataSpy).toHaveBeenCalledOnce();
            expect(suggestMetadataSpy).toHaveBeenCalledWith(COURSE_ID, BRIEF, ProjectType.PLAIN_MAVEN);
            expect(component.title()).toBe('Bounded Stack');
            expect(component.shortName()).toBe('boundedstack');
            expect(component.packageName()).toBe('de.tum.cit.aet.boundedstack');
            expect(component.maxPoints()).toBe(10);
            expect(component.difficulty()).toBe(DifficultyLevel.EASY);
        });

        it('shows that it is working while the call is in flight, and blocks Generate until it lands', () => {
            const pending = new Subject<HyperionMetadataSuggestion>();
            suggestMetadataSpy.mockReturnValue(pending);
            component.brief.set(BRIEF);

            component.suggestMetadata();
            fixture.detectChanges();

            expect(component.suggesting()).toBe(true);
            expect(component.canGenerate()).toBe(false);
            expect(query('hyperion-brief-suggest-running')).not.toBeNull();

            pending.next(SUGGESTION);
            pending.complete();
            fixture.detectChanges();

            expect(component.suggesting()).toBe(false);
            expect(query('hyperion-brief-suggest-running')).toBeNull();
            expect(component.canGenerate()).toBe(true);
        });

        it('refuses to ask while the brief is too short, and says why', () => {
            component.brief.set('too short');
            fixture.detectChanges();

            component.suggestMetadata();

            expect(suggestMetadataSpy).not.toHaveBeenCalled();
            expect(component.canSuggest()).toBe(false);
            expect(query('hyperion-brief-suggest-blocked')).not.toBeNull();
        });

        it('says so and leaves the fields untouched when the request fails', () => {
            suggestMetadataSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            component.brief.set(BRIEF);
            component.editTitle('Ring Buffer');

            component.suggestMetadata();
            fixture.detectChanges();

            expect(component.suggesting()).toBe(false);
            expect(query('hyperion-brief-suggest-failed')).not.toBeNull();
            expect(component.title()).toBe('Ring Buffer');
            expect(component.shortName()).toBe('');
        });

        it('marks the values that came from Hyperion, and stops marking a value the instructor changes', () => {
            component.brief.set(BRIEF);
            fixture.detectChanges();
            expect(query('hyperion-title-suggested')).toBeNull();

            component.suggestMetadata();
            fixture.detectChanges();

            expect(query('hyperion-title-suggested')).not.toBeNull();
            expect(query('hyperion-short-name-suggested')).not.toBeNull();
            expect(query('hyperion-package-name-suggested')).not.toBeNull();
            expect(query('hyperion-max-points-suggested')).not.toBeNull();
            expect(query('hyperion-difficulty-suggested')).not.toBeNull();

            component.editPackageName('de.tum.cit.aet.mine');
            fixture.detectChanges();

            expect(query('hyperion-package-name-suggested')).toBeNull();
            expect(query('hyperion-title-suggested')).not.toBeNull();
        });

        it('replaces an instructor edit only because they asked for another suggestion', () => {
            component.brief.set(BRIEF);
            fillMetadataByHand();
            component.editDifficulty(DifficultyLevel.HARD);

            component.suggestMetadata();

            expect(component.title()).toBe('Bounded Stack');
            expect(component.shortName()).toBe('boundedstack');
            expect(component.packageName()).toBe('de.tum.cit.aet.boundedstack');
            expect(component.maxPoints()).toBe(10);
            expect(component.difficulty()).toBe(DifficultyLevel.EASY);
        });
    });

    describe('the rules a field is judged by', () => {
        beforeEach(() => {
            component.brief.set(BRIEF);
            fillMetadataByHand();
        });

        it.each<[string, boolean]>([
            ['Bo', false],
            ['Bounded Stack', true],
            ['Übung mit Ümläuten', true],
            ['Stack: bounded', false],
            ['Stack (bounded)', false],
            ['a'.repeat(255), true],
            ['a'.repeat(256), false],
        ])('judges the title %s', (title: string, accepted: boolean) => {
            component.editTitle(title);

            expect(component.canGenerate()).toBe(accepted);
        });

        it.each<[string, boolean]>([
            ['bo', false],
            ['boundedstack', true],
            ['b12', true],
            ['1boundedstack', false],
            ['bounded stack', false],
            ['bounded-stack', false],
            ['a'.repeat(36), true],
            ['a'.repeat(37), false],
        ])('judges the short name %s', (shortName: string, accepted: boolean) => {
            component.editShortName(shortName);

            expect(component.canGenerate()).toBe(accepted);
        });

        it.each<[string, boolean]>([
            ['de.tum.cit.aet.boundedstack', true],
            ['boundedstack', true],
            ['de.tum.cit.aet.enum', false],
            ['de.tum.cit.aet.', false],
            ['de.tum.cit.aet.9stack', false],
            ['de tum cit aet', false],
            [`de.tum.cit.aet.${'a'.repeat(86)}`, false],
        ])('judges the package name %s', (packageName: string, accepted: boolean) => {
            component.editPackageName(packageName);

            expect(component.canGenerate()).toBe(accepted);
        });

        it.each<[number, boolean]>([
            [0, false],
            [1, true],
            [10, true],
            [9999, true],
            [10000, false],
        ])('judges %s points', (maxPoints: number, accepted: boolean) => {
            component.editMaxPoints(maxPoints);

            expect(component.canGenerate()).toBe(accepted);
        });

        it('shows the inline error only once the field has been left', () => {
            component.editShortName('bounded stack');
            fixture.detectChanges();
            expect(query('hyperion-short-name-error')).toBeNull();

            component.shortNameTouched.set(true);
            fixture.detectChanges();
            expect(query('hyperion-short-name-error')).not.toBeNull();
        });

        it('judges the package name by the pattern of the selected project type', () => {
            component.editPackageName('de.tum.cit.aet.boundedstack');
            expect(component.canGenerate()).toBe(true);

            // A blackbox project takes a bare identifier, so a dotted package is the wrong shape for it.
            component.projectType.set(ProjectType.MAVEN_BLACKBOX);
            expect(component.canGenerate()).toBe(false);

            component.editPackageName('boundedstack');
            expect(component.canGenerate()).toBe(true);
        });
    });

    describe('what Generate waits for', () => {
        it('stays blocked until every value is there, and says what is missing', () => {
            component.brief.set(BRIEF);
            fixture.detectChanges();

            expect(component.canGenerate()).toBe(false);
            expect(query('hyperion-brief-generate-blocked')?.textContent).toContain('blockedIncomplete');

            component.editTitle('Ring Buffer');
            component.editShortName('ringbuffer');
            component.editPackageName('de.tum.cit.aet.ringbuffer');
            fixture.detectChanges();
            expect(component.canGenerate()).toBe(false);

            component.editMaxPoints(10);
            fixture.detectChanges();

            expect(component.canGenerate()).toBe(true);
            expect(query('hyperion-brief-generate-blocked')).toBeNull();
        });

        it('says that the description is what is missing while there is none', () => {
            fixture.detectChanges();

            expect(query('hyperion-brief-generate-blocked')?.textContent).toContain('blockedBrief');
        });

        it('says that a field is wrong rather than missing once one is filled in badly', () => {
            component.brief.set(BRIEF);
            fillMetadataByHand();
            component.editShortName('1nope');
            fixture.detectChanges();

            expect(component.canGenerate()).toBe(false);
            expect(query('hyperion-brief-generate-blocked')?.textContent).toContain('blockedInvalid');
        });
    });

    describe('starting a run', () => {
        beforeEach(() => {
            component.brief.set(BRIEF);
            component.suggestMetadata();
        });

        it('creates the draft, starts the run, tracks it and opens the run URL', () => {
            setupSucceeds();
            const generateSpy = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));

            component.generate();

            expect(generateSpy).toHaveBeenCalledWith(42, { mode: 'GENERATE', prompt: BRIEF });
            expect(registry.track).toHaveBeenCalledWith({ jobId: 'job-1', exerciseId: 42, courseId: COURSE_ID, exerciseTitle: 'AI draft exercise', mode: 'GENERATE' });
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
            expect(component.visible()).toBe(false);
        });

        it('creates the draft from exactly what the fields hold, and leaves it unreleased', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: CREATED_EXERCISE })));
            vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
            component.editTitle('Ring Buffer');
            component.editShortName('ringbuffer');
            component.editPackageName('de.tum.cit.aet.ringbuffer');
            component.editMaxPoints(25);
            component.editDifficulty(DifficultyLevel.HARD);

            component.generate();

            const draft = setupSpy.mock.calls[0][0];
            expect(draft.title).toBe('Ring Buffer');
            expect(draft.shortName).toBe('ringbuffer');
            expect(draft.packageName).toBe('de.tum.cit.aet.ringbuffer');
            expect(draft.maxPoints).toBe(25);
            expect(draft.difficulty).toBe(DifficultyLevel.HARD);
            expect(draft.releaseDate?.isAfter(dayjs().add(6, 'month'))).toBe(true);
        });

        it('never invents an identifier at submit time', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            component.reset();
            component.brief.set(BRIEF);

            component.generate();

            expect(setupSpy).not.toHaveBeenCalled();
        });

        it('keeps the dialog and the brief when the draft could not be created', () => {
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            component.generate();

            expect(component.setupFailed()).toBe(true);
            expect(component.brief()).toBe(BRIEF);
            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('an identifier another exercise took first', () => {
        const conflict = new HttpErrorResponse({ status: 400, error: { errorKey: 'shortnameAlreadyExists' } });

        beforeEach(() => {
            component.brief.set(BRIEF);
            component.suggestMetadata();
            suggestMetadataSpy.mockClear();
        });

        it('replaces a name Hyperion suggested and retries once, because nobody chose that name', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            setupSpy.mockReturnValueOnce(throwError(() => conflict)).mockReturnValueOnce(of(new HttpResponse({ body: CREATED_EXERCISE })));
            suggestMetadataSpy.mockReturnValue(of({ ...SUGGESTION, shortName: 'boundedstack2', packageName: 'de.tum.cit.aet.boundedstack2' }));
            vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));

            component.generate();

            expect(suggestMetadataSpy).toHaveBeenCalledOnce();
            expect(setupSpy).toHaveBeenCalledTimes(2);
            expect(setupSpy.mock.calls[1][0].shortName).toBe('boundedstack2');
            expect(component.setupFailed()).toBe(false);
            expect(navigateSpy).toHaveBeenCalled();
        });

        it('keeps the fields the instructor typed while it recovers the one that clashed', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            setupSpy.mockReturnValueOnce(throwError(() => conflict)).mockReturnValueOnce(of(new HttpResponse({ body: CREATED_EXERCISE })));
            suggestMetadataSpy.mockReturnValue(of({ ...SUGGESTION, title: 'Something Else', shortName: 'boundedstack2', maxPoints: 10 }));
            vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
            component.editTitle('Ring Buffer');
            component.editMaxPoints(25);

            component.generate();

            const retried = setupSpy.mock.calls[1][0];
            expect(retried.shortName).toBe('boundedstack2');
            expect(retried.title).toBe('Ring Buffer');
            expect(retried.maxPoints).toBe(25);
        });

        it('never replaces a short name the instructor typed themselves, and shows the clash on the field instead', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => conflict));
            component.editShortName('boundedstack');

            component.generate();
            fixture.detectChanges();

            expect(suggestMetadataSpy).not.toHaveBeenCalled();
            expect(setupSpy).toHaveBeenCalledOnce();
            expect(component.shortName()).toBe('boundedstack');
            expect(component.setupFailed()).toBe(false);
            expect(component.provisioning()).toBe(false);
            expect(query('hyperion-short-name-taken')).not.toBeNull();
        });

        it('shows the clash on the title when the title is the one the instructor typed', () => {
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                throwError(() => new HttpErrorResponse({ status: 400, error: { errorKey: 'titleAlreadyExists' } })),
            );
            component.editTitle('Bounded Stack');

            component.generate();
            fixture.detectChanges();

            expect(suggestMetadataSpy).not.toHaveBeenCalled();
            expect(query('hyperion-title-taken')).not.toBeNull();
        });

        it('clears the clash as soon as the instructor changes the field', () => {
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => conflict));
            component.editShortName('boundedstack');
            component.generate();

            component.editShortName('boundedstack2');
            fixture.detectChanges();

            expect(component.conflictingIdentifier()).toBeUndefined();
            expect(query('hyperion-short-name-taken')).toBeNull();
        });

        it('gives up after one retry rather than looping, and hands the name back to the instructor', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => conflict));
            suggestMetadataSpy.mockReturnValue(of(SUGGESTION));

            component.generate();
            fixture.detectChanges();

            expect(suggestMetadataSpy).toHaveBeenCalledOnce();
            expect(setupSpy).toHaveBeenCalledTimes(2);
            expect(component.provisioning()).toBe(false);
            expect(query('hyperion-short-name-taken')).not.toBeNull();
        });

        it('does not retry a failure that is not about a name', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            component.generate();

            expect(suggestMetadataSpy).not.toHaveBeenCalled();
            expect(setupSpy).toHaveBeenCalledOnce();
            expect(component.setupFailed()).toBe(true);
        });
    });

    describe('a start that fails after the draft was created', () => {
        beforeEach(() => {
            component.brief.set(BRIEF);
            component.suggestMetadata();
            setupSucceeds();
            vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            component.generate();
            fixture.detectChanges();
        });

        it('offers both a retry and a delete for the exercise it left behind', () => {
            expect(component.createdExercise()?.id).toBe(42);
            expect(query('hyperion-brief-start-retry')).not.toBeNull();
            expect(query('hyperion-brief-start-delete')).not.toBeNull();
        });

        it('retries without provisioning a second exercise', () => {
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            setupSpy.mockClear();
            const generateSpy = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-2' }));

            component.retryStart();

            expect(setupSpy).not.toHaveBeenCalled();
            expect(generateSpy).toHaveBeenCalledWith(42, { mode: 'GENERATE', prompt: BRIEF });
            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
        });

        it('deletes the draft exercise through the exercise service and resets', () => {
            const deleteSpy = vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

            component.deleteCreatedExercise();

            expect(deleteSpy).toHaveBeenCalledWith(42, false, false);
            expect(component.createdExercise()).toBeUndefined();
            expect(component.brief()).toBe('');
            expect(component.shortName()).toBe('');
            expect(component.maxPoints()).toBeUndefined();
        });

        it('says so when the draft could not be deleted, and keeps it selectable for another attempt', () => {
            vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

            component.deleteCreatedExercise();

            expect(component.deleteFailed()).toBe(true);
            expect(component.createdExercise()?.id).toBe(42);
        });
    });

    it('makes retrying the primary action when the server had no generation capacity', () => {
        component.brief.set(BRIEF);
        component.suggestMetadata();
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorKey: 'generationCapacityUnavailable' } })));

        component.generate();
        fixture.detectChanges();

        const banner = query('hyperion-brief-start-failed');
        expect(banner?.textContent).toContain('error.generationCapacityUnavailable');
        // Nothing was created, so trying again is the answer; the solid primary button is how that is said.
        expect(document.body.querySelector('[data-testid="hyperion-brief-start-retry"] button')?.className).toContain('tum:bg-primary');
    });
});

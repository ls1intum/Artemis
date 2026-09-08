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

    function query(testId: string): Element | null {
        return document.body.querySelector(`[data-testid="${testId}"]`);
    }

    function startFails(): void {
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503, error: { errorKey: 'generationCapacityUnavailable' } })));
        component.brief.set(BRIEF);
        component.generate();
    }

    it.each([
        ['a'.repeat(39), false],
        ['a'.repeat(40), true],
        ['a'.repeat(8000), true],
        ['a'.repeat(8001), false],
        [' '.repeat(60), false],
    ])('validates brief length without metadata inputs (case %#)', (brief, valid) => {
        component.brief.set(brief as string);
        expect(component.canGenerate()).toBe(valid);
    });

    it('renders one brief field and a timing/review note without optional panels or background model calls', () => {
        expect(query('hyperion-brief-input')).not.toBeNull();
        expect(document.body.querySelectorAll('textarea')).toHaveLength(1);
        expect(document.body.querySelector('tum-ui-panel')).toBeNull();
        expect(query('hyperion-brief-derived-metadata')).toBeNull();
        expect(query('hyperion-brief-commitment')?.tagName).toBe('P');
        expect(document.body.querySelector('#hyperion-brief-label')?.getAttribute('for')).toBe('hyperion-brief');
        expect(query('hyperion-brief-input')?.getAttribute('aria-describedby')).toBe('hyperion-brief-hint');
        expect(query('hyperion-title-input')).toBeNull();
        expect(query('hyperion-short-name-input')).toBeNull();
        expect(query('hyperion-brief-suggest')).toBeNull();
        expect(document.body.querySelector('tum-ui-select-button')).toBeNull();
        expect(suggestMetadataSpy).not.toHaveBeenCalled();
    });

    it('shows validation only after leaving the brief and never starts invalid input', () => {
        component.brief.set('short');
        fixture.detectChanges();
        expect(query('hyperion-brief-error')).toBeNull();
        component.briefTouched.set(true);
        fixture.detectChanges();
        expect(query('hyperion-brief-error')).not.toBeNull();
        expect(query('hyperion-brief-input')?.getAttribute('aria-describedby')).toBe('hyperion-brief-hint hyperion-brief-error');
        expect(query('hyperion-brief-input')?.getAttribute('aria-invalid')).toBe('true');
        component.generate();
        expect(suggestMetadataSpy).not.toHaveBeenCalled();
    });

    it('derives metadata, creates a Gradle draft and starts the same brief', () => {
        setupSucceeds();
        const start = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
        const created = vi.spyOn(component.exerciseCreated, 'emit');
        component.brief.set(`  ${BRIEF}  `);
        component.generate();
        expect(suggestMetadataSpy).toHaveBeenCalledExactlyOnceWith(COURSE_ID, BRIEF, ProjectType.GRADLE_GRADLE);
        const [draft, flag] = vi.mocked(programmingExerciseService.automaticSetup).mock.calls[0];
        expect(flag).toBe(true);
        expect(draft).toMatchObject({
            title: SUGGESTION.title,
            shortName: SUGGESTION.shortName,
            packageName: SUGGESTION.packageName,
            maxPoints: 10,
            difficulty: DifficultyLevel.EASY,
            projectType: ProjectType.GRADLE_GRADLE,
            programmingLanguage: 'JAVA',
            problemStatement: '',
            course: { id: COURSE_ID },
            allowOnlineEditor: true,
            allowOfflineIde: true,
        });
        expect(draft.releaseDate?.isAfter(dayjs().add(6, 'month'))).toBe(true);
        expect(start).toHaveBeenCalledExactlyOnceWith(42, { mode: 'GENERATE', prompt: BRIEF });
        expect(created).toHaveBeenCalledExactlyOnceWith(CREATED_EXERCISE);
        expect(registry.track).toHaveBeenCalledWith({ jobId: 'job-1', exerciseId: 42, courseId: COURSE_ID, exerciseTitle: CREATED_EXERCISE.title, mode: 'GENERATE' });
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
        expect(component.visible()).toBe(false);
    });

    it('freezes the brief and course and blocks duplicate submits and closing during preparation', async () => {
        const pending = new Subject<HyperionMetadataSuggestion>();
        suggestMetadataSpy.mockReturnValue(pending);
        setupSucceeds();
        const start = vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
        const back = vi.spyOn(component.backRequested, 'emit');
        component.brief.set(BRIEF);
        component.generate();
        component.generate();
        component.close();
        component.back();
        expect(component.visible()).toBe(true);
        expect(back).not.toHaveBeenCalled();
        expect(component.canGenerate()).toBe(false);
        fixture.detectChanges();
        await fixture.whenStable();
        expect((query('hyperion-brief-input') as HTMLTextAreaElement).disabled).toBe(true);
        component.brief.set('a different brief');
        fixture.componentRef.setInput('courseId', 99);
        pending.next(SUGGESTION);
        expect(suggestMetadataSpy).toHaveBeenCalledTimes(1);
        expect(start).toHaveBeenCalledWith(42, { mode: 'GENERATE', prompt: BRIEF });
        expect(vi.mocked(programmingExerciseService.automaticSetup).mock.calls[0][0].course?.id).toBe(COURSE_ID);
        expect(navigateSpy).toHaveBeenCalledWith(['/course-management', COURSE_ID, 'programming-exercises', 42, 'generation']);
    });

    it('does not create anything when metadata cannot be obtained, and preserves the brief for retry', () => {
        suggestMetadataSpy.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        const setup = vi.spyOn(programmingExerciseService, 'automaticSetup');
        component.brief.set(BRIEF);
        component.generate();
        expect(setup).not.toHaveBeenCalled();
        expect(component.setupFailed()).toBe(true);
        expect(component.provisioning()).toBe(false);
        expect(component.canGenerate()).toBe(true);
        expect(component.brief()).toBe(BRIEF);
    });

    it.each(['titleAlreadyExists', 'shortnameAlreadyExists'])('re-derives once after a concurrent %s conflict', (errorKey) => {
        const replacement = { ...SUGGESTION, title: 'Bounded Stack 2', shortName: 'boundedstack2' };
        suggestMetadataSpy.mockReturnValueOnce(of(SUGGESTION)).mockReturnValueOnce(of(replacement));
        const setup = vi
            .spyOn(programmingExerciseService, 'automaticSetup')
            .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400, error: { errorKey } })))
            .mockReturnValueOnce(of(new HttpResponse({ body: CREATED_EXERCISE })));
        vi.spyOn(generationService, 'generate').mockReturnValue(of({ jobId: 'job-1' }));
        component.brief.set(BRIEF);
        component.generate();
        expect(setup).toHaveBeenCalledTimes(2);
        expect(setup.mock.calls[1][0].shortName).toBe(replacement.shortName);
        expect(suggestMetadataSpy).toHaveBeenCalledTimes(2);
    });

    it('bounds name conflict recovery and exposes a retryable failure', () => {
        const setup = vi
            .spyOn(programmingExerciseService, 'automaticSetup')
            .mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, error: { errorKey: 'titleAlreadyExists' } })));
        component.brief.set(BRIEF);
        component.generate();
        expect(setup).toHaveBeenCalledTimes(2);
        expect(suggestMetadataSpy).toHaveBeenCalledTimes(2);
        expect(component.setupFailed()).toBe(true);
        expect(component.canGenerate()).toBe(true);
    });

    it('does not retry unrelated setup failures', () => {
        const setup = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
        component.brief.set(BRIEF);
        component.generate();
        expect(setup).toHaveBeenCalledTimes(1);
        expect(component.setupFailed()).toBe(true);
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('rejects a setup response without an exercise ID', () => {
        vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse<ProgrammingExercise>({})));
        const start = vi.spyOn(generationService, 'generate');
        component.brief.set(BRIEF);
        component.generate();
        expect(start).not.toHaveBeenCalled();
        expect(component.setupFailed()).toBe(true);
    });

    it('keeps a created draft and retries its run without deriving or creating again', () => {
        startFails();
        expect(component.createdExercise()?.id).toBe(42);
        expect(component.canGenerate()).toBe(false);
        fixture.detectChanges();
        expect(query('hyperion-brief-start-failed')).not.toBeNull();
        component.generate();
        component.brief.set('changed after the failed start');
        vi.mocked(generationService.generate).mockReturnValue(of({ jobId: 'retry-job' }));
        component.retryStart();
        expect(suggestMetadataSpy).toHaveBeenCalledTimes(1);
        expect(programmingExerciseService.automaticSetup).toHaveBeenCalledTimes(1);
        expect(generationService.generate).toHaveBeenLastCalledWith(42, { mode: 'GENERATE', prompt: BRIEF });
        expect(navigateSpy).toHaveBeenCalledOnce();
    });

    it.each([0, 409])('reattaches to an owned run after an ambiguous start response (%s)', (status) => {
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
        vi.spyOn(generationService, 'getStatus').mockReturnValue(
            of({
                jobId: 'accepted-job',
                running: true,
                events: [],
                fileChanges: [],
                ownedByCaller: true,
                cancellable: true,
                revertAvailable: false,
                accountingState: 'PENDING',
                artifactsRetained: false,
            }),
        );
        component.brief.set(BRIEF);
        component.generate();
        expect(generationService.getStatus).toHaveBeenCalledExactlyOnceWith(42);
        expect(registry.track).toHaveBeenCalledWith(expect.objectContaining({ jobId: 'accepted-job', exerciseId: 42 }));
        expect(programmingExerciseService.automaticSetup).toHaveBeenCalledTimes(1);
        expect(component.visible()).toBe(false);
    });

    it("does not attach to another instructor's run", () => {
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));
        vi.spyOn(generationService, 'getStatus').mockReturnValue(
            of({
                jobId: 'other-job',
                running: true,
                events: [],
                fileChanges: [],
                ownedByCaller: false,
                cancellable: false,
                revertAvailable: false,
                accountingState: 'INCOMPLETE',
                artifactsRetained: false,
            }),
        );
        component.brief.set(BRIEF);
        component.generate();
        expect(registry.track).not.toHaveBeenCalled();
        expect(component.startError()?.status).toBe(409);
        expect(component.createdExercise()?.id).toBe(42);
    });

    it('allows back and close while idle without starting generation', () => {
        const back = vi.spyOn(component.backRequested, 'emit');
        component.back();
        expect(back).toHaveBeenCalledOnce();
        component.close();
        expect(component.visible()).toBe(false);
        expect(suggestMetadataSpy).not.toHaveBeenCalled();
    });

    it('preserves the draft when an ambiguous start cannot be reconciled', () => {
        setupSucceeds();
        vi.spyOn(generationService, 'generate').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        vi.spyOn(generationService, 'getStatus').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
        component.brief.set(BRIEF);
        component.generate();
        expect(component.createdExercise()?.id).toBe(42);
        expect(component.startError()?.status).toBe(0);
        expect(component.busy()).toBe(false);
        expect(navigateSpy).not.toHaveBeenCalled();
    });

    it('prevents deletion or duplicate retry while a start is pending', () => {
        startFails();
        const pending = new Subject<{ jobId: string }>();
        vi.mocked(generationService.generate).mockReturnValue(pending);
        const remove = vi.spyOn(programmingExerciseService, 'delete');
        component.retryStart();
        component.retryStart();
        component.deleteCreatedExercise();
        expect(generationService.generate).toHaveBeenCalledTimes(2);
        expect(remove).not.toHaveBeenCalled();
    });

    it('deletes only the unused draft and preserves the brief', () => {
        startFails();
        const pending = new Subject<HttpResponse<void>>();
        const remove = vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(pending);
        component.deleteCreatedExercise();
        component.deleteCreatedExercise();
        component.retryStart();
        expect(remove).toHaveBeenCalledExactlyOnceWith(42, false, false);
        expect(generationService.generate).toHaveBeenCalledTimes(1);
        pending.next(new HttpResponse<void>({}));
        expect(component.createdExercise()).toBeUndefined();
        expect(component.startError()).toBeUndefined();
        expect(component.brief()).toBe(BRIEF);
        expect(component.canGenerate()).toBe(true);
    });

    it('keeps recovery available when deletion fails', () => {
        startFails();
        vi.spyOn(programmingExerciseService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
        component.deleteCreatedExercise();
        expect(component.deleteFailed()).toBe(true);
        expect(component.createdExercise()?.id).toBe(42);
        expect(component.busy()).toBe(false);
    });

    it('does not create or delete without the corresponding user action', () => {
        const remove = vi.spyOn(programmingExerciseService, 'delete');
        const start = vi.spyOn(generationService, 'generate');
        component.retryStart();
        component.deleteCreatedExercise();
        expect(start).not.toHaveBeenCalled();
        expect(remove).not.toHaveBeenCalled();
    });
});

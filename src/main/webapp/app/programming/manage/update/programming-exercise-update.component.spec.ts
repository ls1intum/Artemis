import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap } from '@angular/router';
import { ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { AI_MODE_DEFAULT_POINTS, LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, ProgrammingExerciseUpdateComponent } from 'app/programming/manage/update/programming-exercise-update.component';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import '@angular/localize/init';
import { ProgrammingLanguageFeatureService } from 'app/programming/shared/services/programming-language-feature/programming-language-feature.service';
import { LockRepositoryPolicy, SubmissionPenaltyPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInformationComponent } from 'app/programming/manage/update/update-components/information/programming-exercise-information.component';
import { ProgrammingExerciseModeComponent } from 'app/programming/manage/update/update-components/mode/programming-exercise-mode.component';
import { ProgrammingExerciseLanguageComponent } from 'app/programming/manage/update/update-components/language/programming-exercise-language.component';
import { ProgrammingExerciseGradingComponent } from 'app/programming/manage/update/update-components/grading/programming-exercise-grading.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import * as Utils from 'app/exercise/course-exercises/course-utils';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { AlertService, AlertType } from 'app/foundation/service/alert.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_HYPERION, MODULE_FEATURE_THEIA } from 'app/app.constants';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';
import { APP_NAME_PATTERN_FOR_SWIFT, MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH, PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN } from 'app/foundation/constants/input.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockProvider } from 'ng-mocks';
import { ProgrammingExerciseInstructionAnalysisService } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { FileService } from 'app/foundation/service/file.service';
import { AUTO_START_EXERCISE_GENERATION_PROMPT } from 'app/hyperion/exercise-generation/exercise-generation.constants';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ProfileInfo, ProgrammingLanguageFeature } from 'app/core/layouts/profiles/profile-info.model';
import { Signal, signal } from '@angular/core';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ProgrammingExerciseSharingService } from '../services/programming-exercise-sharing.service';
import { ExerciseEditorSyncService } from 'app/exercise/synchronization/services/exercise-editor-sync.service';
import { ExerciseMetadataSyncService } from 'app/exercise/synchronization/services/exercise-metadata-sync.service';
import { ProblemStatementSyncService } from 'app/exercise/synchronization/services/problem-statement-sync.service';
import { DialogService } from 'primeng/dynamicdialog';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';

vi.mock('y-monaco', () => ({
    // Use a real `function` (not an arrow) so the production code can invoke it with `new`.
    MonacoBinding: vi.fn(function (this: any) {
        this.destroy = vi.fn();
    }),
}));

const AUTO_START_EXERCISE_GENERATION_STATE = 'autoStartExerciseGeneration';

/**
 * Typed view onto the protected `viewChild` signals so the spec can override them
 * without a blanket `(component as any)` cast. The shape mirrors the component declaration.
 */
type ProgrammingExerciseUpdateInternals = ProgrammingExerciseUpdateComponent & {
    exerciseInfoComponent: Signal<ProgrammingExerciseInformationComponent | undefined>;
    exerciseDifficultyComponent: Signal<ProgrammingExerciseModeComponent | undefined>;
    exerciseLanguageComponent: Signal<ProgrammingExerciseLanguageComponent | undefined>;
    exerciseGradingComponent: Signal<ProgrammingExerciseGradingComponent | undefined>;
    exercisePlagiarismComponent: Signal<ExerciseUpdatePlagiarismComponent | undefined>;
};
const internals = (c: ProgrammingExerciseUpdateComponent): ProgrammingExerciseUpdateInternals => c as ProgrammingExerciseUpdateInternals;

describe('ProgrammingExerciseUpdateComponent', () => {
    setupTestBed({ zoneless: true });

    const courseId = 1;
    const course = { id: courseId } as Course;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '123' }) },
        url: of([{ path: 'programming-exercises' }] as UrlSegment[]),
        // ngOnInit pipes url through switchMap(() => params); a real ActivatedRoute always exposes a params
        // stream, so provide an empty one here. Tests that need concrete params override route.params.
        params: of({}),
    } as ActivatedRoute;
    let comp: ProgrammingExerciseUpdateComponent;
    let fixture: ComponentFixture<ProgrammingExerciseUpdateComponent>;
    let programmingExerciseService: ProgrammingExerciseService;
    let courseService: CourseManagementService;
    let exerciseGroupService: ExerciseGroupService;
    let programmingExerciseFeatureService: ProgrammingLanguageFeatureService;
    let alertService: AlertService;
    let profileService: ProfileService;
    let fileService: FileService;
    let programmingExerciseSharingService: ProgrammingExerciseSharingService;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, OwlNativeDateTimeModule],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: ProgrammingExerciseInstructionAnalysisService, useClass: ProgrammingExerciseInstructionAnalysisService },
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: ProfileService, useClass: MockProfileService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(CalendarService),
                MockProvider(ExerciseEditorSyncService),
                MockProvider(ExerciseMetadataSyncService),
                {
                    provide: ProblemStatementSyncService,
                    useValue: {
                        init: vi.fn().mockReturnValue({ doc: {}, text: { toString: () => '' }, awareness: {} }),
                        reset: vi.fn(),
                        stateReplaced$: of(),
                        initialSyncFinalized$: of({ contentChangedDuringFinalize: false, contentDivergedFromFallback: false, finalContent: '' }),
                    },
                },
                MockProvider(DialogService),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingExerciseUpdateComponent);
        comp = fixture.componentInstance;
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
        courseService = TestBed.inject(CourseManagementService);
        exerciseGroupService = TestBed.inject(ExerciseGroupService);
        programmingExerciseFeatureService = TestBed.inject(ProgrammingLanguageFeatureService);
        alertService = TestBed.inject(AlertService);
        profileService = TestBed.inject(ProfileService);
        fileService = TestBed.inject(FileService);
        programmingExerciseSharingService = TestBed.inject(ProgrammingExerciseSharingService);
        localStorageService = TestBed.inject(LocalStorageService);

        const programmingLanguageFeature = {
            programmingLanguage: ProgrammingLanguage.JAVA,
            sequentialTestRuns: true,
            staticCodeAnalysis: true,
            plagiarismCheckSupported: true,
            packageNameRequired: true,
            checkoutSolutionRepositoryAllowed: true,
            projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
            auxiliaryRepositoriesSupported: true,
        } as ProgrammingLanguageFeature;

        const newProfileInfo = new ProfileInfo();
        newProfileInfo.activeProfiles = [];
        newProfileInfo.activeModuleFeatures = [];
        newProfileInfo.programmingLanguageFeatures = [programmingLanguageFeature];
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(newProfileInfo);

        comp.editMode.set('advanced');

        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        route.data = of({ programmingExercise });
        vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(programmingLanguageFeature);
        // Assign the mock class directly: under Vitest, wrapping it in vi.fn().mockImplementation(() => new ...)
        // is not usable as a constructor (`new ResizeObserver()` throws "is not a constructor").
        global.ResizeObserver = MockResizeObserver as unknown as typeof ResizeObserver;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('AI create flow: ngOnInit leaves the problem statement EMPTY when AI mode is active (the synchronous readme load must be skipped)', () => {
        // In AI mode the language readme (a worked sample, e.g. the Java sorting exercise) must NOT load: ngOnInit sets selectedProgrammingLanguage synchronously, which runs
        // loadProgrammingLanguageTemplate. On the AI path that has to leave the statement empty, otherwise a from-scratch run would treat the sample as an authoritative spec ("bubble sort").
        vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_HYPERION);
        const getTemplateFile = vi.spyOn(fileService, 'getTemplateFile').mockReturnValue(of('# A sorting sample readme long enough to be treated as a real instructor spec'));
        const route = TestBed.inject(ActivatedRoute);
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.params = of({ courseId });
        comp.editMode.set('ai'); // the user has opted into AI mode before the language template resolves

        comp.ngOnInit();

        expect(comp.showGenerateWithAi()).toBe(true);
        expect(getTemplateFile).not.toHaveBeenCalled();
        expect(comp.programmingExercise.problemStatement).toBe('');
    });

    describe('initializeEditMode', () => {
        it('should set isSimpleMode to true if localStorage has value "true"', () => {
            localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, true);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBeTruthy();
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBe(true);
        });

        it('should set isSimpleMode to false if localStorage has value "false"', () => {
            localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, false);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBe(false);
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBe(false);
        });

        it('should set isSimpleMode to true if not present in local storage', () => {
            localStorageService.remove(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE);

            fixture.detectChanges();

            expect(comp.isSimpleMode()).toBe(true);
        });
    });

    it('switchEditMode should toggle isSimpleMode and update local storage', () => {
        localStorageService.store<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE, true);
        fixture.detectChanges();
        expect(comp.isSimpleMode()).toBeTruthy(); // ensure the assumed initial state isSimpleMode = true holds

        comp.switchEditMode();

        expect(comp.isSimpleMode()).toBeFalsy();
        expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBe(false);
    });

    describe('AI mode (create-only third mode)', () => {
        beforeEach(() => {
            const exercise = new ProgrammingExercise(undefined, undefined);
            exercise.programmingLanguage = ProgrammingLanguage.JAVA;
            comp.programmingExercise = exercise;
            comp.backupExercise = {} as ProgrammingExercise;
            // Create + Hyperion + not an import → showGenerateWithAi() is true, so AI mode is eligible.
            comp.hyperionEnabled = true;
        });

        it('entering AI mode blanks the problem statement and uses the lean AI field set; AI mode is never persisted to localStorage', () => {
            // An edited/leftover statement that differs from the loaded readme triggers the discard guard; accept it.
            const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true);
            comp.programmingExercise.problemStatement = '# A leftover readme sample';

            comp.setEditMode('ai');

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(comp.isAiMode()).toBe(true);
            expect(comp.isSimpleMode()).toBe(true); // AI reuses the simple layout machinery
            expect(comp.programmingExercise.problemStatement).toBe('');
            // The radically lean AI map shows ONLY the language and the problem-statement brief; everything else is auto-generated/defaulted and hidden.
            expect(comp.isEditFieldDisplayedRecord().programmingLanguage).toBe(true);
            expect(comp.isEditFieldDisplayedRecord().problemStatement).toBe(true);
            expect(comp.isEditFieldDisplayedRecord().shortName).toBe(false);
            expect(comp.isEditFieldDisplayedRecord().projectType).toBe(false);
            expect(comp.isEditFieldDisplayedRecord().points).toBe(false);
            expect(comp.isEditFieldDisplayedRecord().timeline).toBe(false);
            // AI mode is ephemeral: only simple/advanced are written to localStorage.
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).not.toBe('ai');
        });

        it('guards against silently discarding an edited problem statement: on cancel it stays in the current mode', () => {
            const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
            comp.programmingExercise.problemStatement = '# A hand-edited statement the instructor would lose';

            comp.setEditMode('ai');

            expect(confirmSpy).toHaveBeenCalledOnce();
            expect(comp.isAiMode()).toBe(false); // cancelled — mode unchanged
            expect(comp.programmingExercise.problemStatement).toBe('# A hand-edited statement the instructor would lose'); // not wiped
        });

        it('persists the manual preference when leaving AI mode for a manual mode', () => {
            comp.setEditMode('ai');
            comp.setEditMode('advanced');
            expect(comp.isAiMode()).toBe(false);
            expect(localStorageService.retrieve<boolean>(LOCAL_STORAGE_KEY_IS_SIMPLE_MODE)).toBe(false);
        });

        it('falls back to simple mode if AI eligibility disappears while in AI mode', () => {
            // Keep Hyperion on through ngOnInit so the constructor effects settle with AI mode eligible.
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_HYPERION);
            fixture.detectChanges();

            comp.setEditMode('ai');
            expect(comp.isAiMode()).toBe(true);

            // Eligibility goes away (e.g. Hyperion disabled): the guard effect must drop AI mode so the footer never routes to a path that cannot run.
            comp.hyperionEnabled = false;
            fixture.detectChanges();
            expect(comp.isAiMode()).toBe(false);
            expect(comp.editMode()).toBe('simple');
        });

        it('seeds the hidden required fields with valid defaults when entering AI mode', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
            comp.programmingExercise.maxPoints = undefined;
            comp.programmingExercise.packageName = undefined;
            comp.programmingExercise.shortName = undefined;

            comp.setEditMode('ai');

            // Points default so the persist gate is satisfied without asking the instructor to grade unseen content.
            expect(comp.programmingExercise.maxPoints).toBe(AI_MODE_DEFAULT_POINTS);
            // A Java/Kotlin package is required to persist; seed the house default.
            expect(comp.programmingExercise.packageName).toBe('de.tum.in.ase');
            // Short name is auto-derived and must satisfy the ^[a-zA-Z][a-zA-Z0-9]* pattern.
            expect(comp.programmingExercise.shortName).toMatch(/^[a-zA-Z][a-zA-Z0-9]*$/);
            expect(comp.programmingExercise.shortName!.length).toBeGreaterThanOrEqual(3);
        });

        it('derives the title and short name from the brief until the instructor edits the title', () => {
            comp.setEditMode('ai');
            comp.programmingExercise.title = undefined;

            comp.onAiBriefChange('Implement a Roman numeral converter that handles 1..3999');

            // The lead-in "Implement a " is stripped; the disallowed ".." collapses to a space so the placeholder satisfies the server title pattern.
            expect(comp.programmingExercise.title).toBe('Roman numeral converter that handles 1 3999');
            const autoShortName = comp.programmingExercise.shortName;
            expect(autoShortName).toMatch(/^[a-zA-Z][a-zA-Z0-9]*$/);

            // A second brief keystroke keeps re-deriving while the title is still the auto-seeded value.
            comp.onAiBriefChange('Build a balanced binary search tree');
            expect(comp.programmingExercise.title).toBe('Balanced binary search tree');

            // Once the instructor hand-edits the title, the brief stops overwriting it.
            comp.programmingExercise.title = 'My exact title';
            comp.onAiBriefChange('Some completely different brief text');
            expect(comp.programmingExercise.title).toBe('My exact title');
        });

        it('derives a title that always satisfies the server title pattern even from free-prose briefs (no colon, commas, slashes)', () => {
            // Regression: a brief without an early colon used to yield a raw slice containing commas/periods, which the server rejects with titlePatternInvalid (400) — and because the
            // lean page hides the title field, the persist failed silently. The derived placeholder must be valid by construction.
            comp.setEditMode('ai');
            const pattern = /^[a-zA-Z0-9 _-]+$/;

            for (const brief of [
                'For week 5 of our intro course, right after the recursion lecture, I would like a medium exercise on file-system sizes.',
                'Build a parser for arithmetic expressions (with +, -, *, /) and report division-by-zero errors clearly.',
                'студенты: пишут кэш', // non-Latin → must still fall back to a valid title
            ]) {
                comp.programmingExercise.title = undefined;
                comp.onAiBriefChange(brief);
                const title = comp.programmingExercise.title ?? '';
                expect(title.length).toBeGreaterThanOrEqual(3);
                expect(title).toMatch(pattern);
            }
        });

        it('re-defaults the package name when the language changes in AI mode', () => {
            vi.spyOn(window, 'confirm').mockReturnValue(true); // language change may prompt on unsaved changes
            comp.setEditMode('ai');

            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);
            expect(comp.programmingExercise.packageName).toBe('de.tum.in.ase');

            comp.onProgrammingLanguageChange(ProgrammingLanguage.PYTHON);
            // Python has no package concept — clear it so a stale Java package can never block the persist.
            expect(comp.programmingExercise.packageName).toBeUndefined();

            comp.onProgrammingLanguageChange(ProgrammingLanguage.GO);
            expect(comp.programmingExercise.packageName).toBe('exercise');
        });
    });

    describe('save', () => {
        it('should call update service on save and refresh calendar events for existing entity', () => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            vi.spyOn(programmingExerciseService, 'update').mockReturnValue(of(new HttpResponse({ body: entity })));
            const calendarService = TestBed.inject(CalendarService);
            const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.isSaving).toBe(false);
            expect(refreshSpy).toHaveBeenCalledOnce();
        });

        it('should call create service on save and refresh calendar events for new entity', () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            const calendarService = TestBed.inject(CalendarService);
            const refreshSpy = vi.spyOn(calendarService, 'reloadEvents');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(comp.isSaving).toBe(false);
            expect(refreshSpy).toHaveBeenCalledOnce();
        });

        it('should trim the exercise title before saving', () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            entity.title = 'My Exercise   ';
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 1,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;

            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseService.automaticSetup).toHaveBeenCalledWith(entity);
            expect(entity.title).toBe('My Exercise');
        });

        it('should send the assessmentType on saving', () => {
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 1;
            entity.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            entity.releaseDate = dayjs();
            vi.spyOn(programmingExerciseService, 'update').mockReturnValue(
                of(
                    new HttpResponse({
                        body: entity,
                    }),
                ),
            );

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;

            comp.save();

            expect(programmingExerciseService.update).toHaveBeenCalledWith(entity, {});
            expect(comp.programmingExercise.assessmentType).toBe(AssessmentType.SEMI_AUTOMATIC);
        });

        it('should fail on error', () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.id = 1;
            vi.spyOn(programmingExerciseService, 'update').mockReturnValue(
                throwError(
                    () =>
                        new HttpResponse({
                            headers: new HttpHeaders({ 'X-artemisApp-alert': 'error-message' }),
                        }),
                ),
            );
            const alertSpy = vi.spyOn(alertService, 'addAlert');
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            // WHEN
            comp.save();

            // THEN
            expect(comp.isSaving).toBe(false);
            expect(alertSpy).toHaveBeenCalledWith({
                type: AlertType.DANGER,
                message: 'error-message',
                disableTranslation: true,
            });
        });

        describe('should set project type in invalid default value is selected', () => {
            beforeEach(() => {
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.releaseDate = dayjs(); // We will get a warning if we do not set a release date
                programmingExercise.projectType = ProjectType.PLAIN_GRADLE;
                programmingExercise.course = course;

                comp.programmingExercise = programmingExercise;
                comp.backupExercise = {} as ProgrammingExercise;
                route.url = of([{ path: 'new' }] as UrlSegment[]);
                fixture.detectChanges();
            });

            it('should set valid project type in simple mode if default project type (gradle) is not supported', () => {
                comp.editMode.set('simple');
                comp.projectTypes = [ProjectType.PLAIN_MAVEN];
                fixture.changeDetectorRef.detectChanges();

                comp.save();

                fixture.changeDetectorRef.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
            });

            it('should keep gradle if gradle is supported', () => {
                comp.editMode.set('simple');
                comp.projectTypes = [ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE];
                fixture.changeDetectorRef.detectChanges();

                comp.save();

                fixture.changeDetectorRef.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_GRADLE);
            });

            it('should keep gradle not in creation modal', () => {
                comp.editMode.set('simple');
                comp.isCreate = false;
                comp.projectTypes = [ProjectType.PLAIN_MAVEN];
                fixture.changeDetectorRef.detectChanges();

                comp.save();

                fixture.changeDetectorRef.detectChanges();
                expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_GRADLE);
            });
        });
    });

    describe('save with AI', () => {
        beforeEach(() => {
            // The happy-path tests below exercise a valid form; the new invalid-form guard is covered by its own test.
            vi.spyOn(comp, 'getInvalidReasons').mockReturnValue([]);
        });

        it('bails on an invalid form, surfaces the invalid reasons and does not start generation', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.course = course;
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const reasons = [{ translateKey: 'artemisApp.exercise.form.title.undefined', translateValues: {} }];
            (comp.getInvalidReasons as unknown as ReturnType<typeof vi.fn>).mockReturnValue(reasons);
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup');
            const alertSpy = vi.spyOn(alertService, 'addAlert');

            comp.saveExerciseWithAi();

            expect(setupSpy).not.toHaveBeenCalled();
            expect(comp.isGeneratingWithAi()).toBe(false);
            expect(alertSpy).toHaveBeenCalledWith(expect.objectContaining({ message: 'artemisApp.exercise.form.title.undefined' }));
        });

        it('should call automatic setup with empty repositories and navigate to the instructor code editor with auto-start state', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.releaseDate = dayjs();
            entity.course = course;

            const savedEntity = new ProgrammingExercise(course, undefined);
            savedEntity.id = 7;
            savedEntity.course = course;
            savedEntity.templateParticipation = { id: 11 } as any;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const response$ = new Subject<HttpResponse<ProgrammingExercise>>();
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(response$);
            const router = TestBed.inject(Router) as unknown as MockRouter;

            comp.saveExerciseWithAi();

            expect(comp.isGeneratingWithAi()).toBe(true);
            expect(setupSpy).toHaveBeenCalledWith(entity, true);

            response$.next(new HttpResponse({ body: savedEntity }));

            expect(router.navigate).toHaveBeenCalledWith(['course-management', courseId, 'programming-exercises', savedEntity.id, 'code-editor', 'ide', 'test'], {
                state: { [AUTO_START_EXERCISE_GENERATION_STATE]: true, [AUTO_START_EXERCISE_GENERATION_PROMPT]: '' },
            });
            expect(comp.isGeneratingWithAi()).toBe(false);
        });

        it('threads the instructor brief from "Generate entire exercise" into the auto-start navigation state', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.course = course;
            const savedEntity = new ProgrammingExercise(course, undefined);
            savedEntity.id = 7;
            savedEntity.course = course;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const response$ = new Subject<HttpResponse<ProgrammingExercise>>();
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(response$);
            // saveWithAi goes straight through the pre-update modal check (no confirmation dialog); stub it to invoke its callback directly.
            vi.spyOn(comp, 'saveWithModalCheck' as any).mockImplementation((fn: any) => fn());
            const router = TestBed.inject(Router) as unknown as MockRouter;

            // The child emits the "Your Requirements" brief via briefChange → currentBrief; the footer's "Generate entire exercise" action routes it through aiGenerate → saveWithAi(currentBrief()).
            comp.saveWithAi('  Implement a bounded LRU cache  ');
            response$.next(new HttpResponse({ body: savedEntity }));

            expect(router.navigate).toHaveBeenCalledWith(expect.anything(), {
                state: { [AUTO_START_EXERCISE_GENERATION_STATE]: true, [AUTO_START_EXERCISE_GENERATION_PROMPT]: 'Implement a bounded LRU cache' },
            });
        });

        it('should navigate to the instructor code editor with auto-start state after AI exercise creation in exam mode', () => {
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs();
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = 3;
            exerciseGroup.exam = { id: 9, course } as any;
            entity.exerciseGroup = exerciseGroup;

            const savedEntity = new ProgrammingExercise(undefined, exerciseGroup);
            savedEntity.id = 7;
            savedEntity.templateParticipation = { id: 11 } as any;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const response$ = new Subject<HttpResponse<ProgrammingExercise>>();
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(response$);
            const router = TestBed.inject(Router) as unknown as MockRouter;

            comp.saveExerciseWithAi();

            response$.next(new HttpResponse({ body: savedEntity }));

            expect(router.navigate).toHaveBeenCalledWith(['course-management', courseId, 'programming-exercises', savedEntity.id, 'code-editor', 'ide', 'test'], {
                state: { [AUTO_START_EXERCISE_GENERATION_STATE]: true, [AUTO_START_EXERCISE_GENERATION_PROMPT]: '' },
            });
        });

        it('should fall back to regular save when hyperion is disabled', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.releaseDate = dayjs();
            entity.course = course;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = false;

            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: entity })));

            comp.saveExerciseWithAi();

            expect(setupSpy).toHaveBeenCalledWith(entity);
        });

        it('should reset generating flag on save error', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.releaseDate = dayjs();
            entity.course = course;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const response$ = new Subject<HttpResponse<ProgrammingExercise>>();
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(response$);

            comp.saveExerciseWithAi();
            expect(comp.isGeneratingWithAi()).toBe(true);

            response$.error(new HttpErrorResponse({ headers: new HttpHeaders({ 'X-artemisApp-alert': 'error-message' }) }));

            expect(comp.isGeneratingWithAi()).toBe(false);
            expect(comp.isSaving).toBe(false);
        });

        it('should treat null id as a new exercise and use empty repositories setup', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.releaseDate = dayjs();
            entity.course = course;
            entity.id = null as unknown as number;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;

            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: entity })));

            comp.saveExerciseWithAi();

            expect(setupSpy).toHaveBeenCalledWith(entity, true);
        });
    });

    describe('saveWithAi (no confirmation dialog)', () => {
        it('starts the from-scratch generation directly — the explicit, self-describing footer action needs no extra confirmation', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.releaseDate = dayjs();
            entity.course = course;
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.hyperionEnabled = true;
            // Take the from-scratch generation branch (not the plain-save early return) in saveExerciseWithAi.
            comp.isImportFromExistingExercise = false;
            comp.isImportFromFile = false;
            comp.isImportFromSharing = false;
            vi.spyOn(comp, 'getInvalidReasons').mockReturnValue([]);
            const setupSpy = vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(of(new HttpResponse({ body: entity })));

            comp.saveWithAi('Implement a bounded stack');

            // No confirmation step: the from-scratch generation runs immediately with empty repositories (emptyRepositories=true).
            expect(setupSpy).toHaveBeenCalledWith(entity, true);
        });
    });

    describe('generate with AI visibility', () => {
        // The generation-supported set is now server-driven; it gates whether the entire-exercise action can run (generationLanguageSupported),
        // NOT whether the action area is shown. showGenerateWithAi is purely STRUCTURAL (create + Hyperion + not an import), independent of language (R2).
        const SERVER_SUPPORTED = [ProgrammingLanguage.JAVA, ProgrammingLanguage.KOTLIN, ProgrammingLanguage.PYTHON];

        it('should show structurally regardless of language, even before the supported set has loaded (R2)', () => {
            // Supported set still at its empty default (server set has not arrived) — the action area must still be shown so the capability stays discoverable.
            const entity = new ProgrammingExercise(course, undefined);
            entity.programmingLanguage = ProgrammingLanguage.JAVA;

            comp.programmingExercise = entity;
            comp.hyperionEnabled = true;
            comp.isImportFromExistingExercise = false;
            comp.isImportFromFile = false;
            comp.isImportFromSharing = false;

            expect(comp.supportedGenerationLanguages()).toEqual([]);
            expect(comp.showGenerateWithAi()).toBe(true);

            // A language with no generation profile (e.g. OCaml) still shows the action area — it is rendered-but-disabled in the child, not removed.
            const ocamlExercise = new ProgrammingExercise(course, undefined);
            ocamlExercise.programmingLanguage = ProgrammingLanguage.OCAML;
            comp.programmingExercise = ocamlExercise;
            expect(comp.showGenerateWithAi()).toBe(true);
        });

        it('should hide for imports and edits (structural gate)', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.programmingLanguage = ProgrammingLanguage.JAVA;
            comp.programmingExercise = entity;
            comp.hyperionEnabled = true;

            comp.isImportFromExistingExercise = true;
            expect(comp.showGenerateWithAi()).toBe(false);
            comp.isImportFromExistingExercise = false;

            comp.isImportFromFile = true;
            expect(comp.showGenerateWithAi()).toBe(false);
            comp.isImportFromFile = false;

            comp.isImportFromSharing = true;
            expect(comp.showGenerateWithAi()).toBe(false);
            comp.isImportFromSharing = false;

            // Editing an existing exercise (id set) hides the create-only action.
            const existing = new ProgrammingExercise(course, undefined);
            existing.programmingLanguage = ProgrammingLanguage.JAVA;
            existing.id = 5;
            comp.programmingExercise = existing;
            expect(comp.showGenerateWithAi()).toBe(false);
        });

        it('should report generationLanguageSupported only for languages in the server set (fail-closed before load)', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.programmingLanguage = ProgrammingLanguage.JAVA;
            comp.programmingExercise = entity;
            comp.hyperionEnabled = true;

            // Before the set loads, the language is treated as unsupported (action rendered-but-disabled).
            expect(comp.generationLanguageSupported()).toBe(false);

            comp.supportedGenerationLanguages.set(SERVER_SUPPORTED);
            expect(comp.generationLanguageSupported()).toBe(true);

            const ocamlExercise = new ProgrammingExercise(course, undefined);
            ocamlExercise.programmingLanguage = ProgrammingLanguage.OCAML;
            comp.programmingExercise = ocamlExercise;
            // OCaml has only a best-effort server profile and is not in the supported set.
            expect(comp.generationLanguageSupported()).toBe(false);
        });

        it('should still show when id is null', () => {
            const entity = new ProgrammingExercise(course, undefined);
            entity.programmingLanguage = ProgrammingLanguage.JAVA;
            entity.id = null as unknown as number;

            comp.programmingExercise = entity;
            comp.hyperionEnabled = true;
            comp.isImportFromExistingExercise = false;
            comp.isImportFromFile = false;
            comp.isImportFromSharing = false;

            expect(comp.showGenerateWithAi()).toBe(true);
        });

        it('should load the supported set from the server on init when hyperion is enabled (S1)', () => {
            const hyperionService = TestBed.inject(HyperionExerciseGenerationService);
            const getSupported = vi.spyOn(hyperionService, 'getSupportedLanguages').mockReturnValue(of(SERVER_SUPPORTED));
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_HYPERION);

            comp.ngOnInit();

            expect(getSupported).toHaveBeenCalledOnce();
            expect(comp.supportedGenerationLanguages()).toEqual(SERVER_SUPPORTED);
        });

        it('should keep the set empty (fail-closed) when the server call errors (S5)', () => {
            const hyperionService = TestBed.inject(HyperionExerciseGenerationService);
            vi.spyOn(hyperionService, 'getSupportedLanguages').mockReturnValue(throwError(() => new Error('boom')));
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => feature === MODULE_FEATURE_HYPERION);

            comp.ngOnInit();

            expect(comp.supportedGenerationLanguages()).toEqual([]);
        });

        it('should never call the server when hyperion is disabled', () => {
            const hyperionService = TestBed.inject(HyperionExerciseGenerationService);
            const getSupported = vi.spyOn(hyperionService, 'getSupportedLanguages').mockReturnValue(of(SERVER_SUPPORTED));
            vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

            comp.ngOnInit();

            expect(getSupported).not.toHaveBeenCalled();
        });
    });

    describe('exam mode', () => {
        const examId = 1;
        const exerciseGroupId = 1;
        const exerciseGroup = new ExerciseGroup();
        exerciseGroup.id = exerciseGroupId;
        const expectedExamProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedExamProgrammingExercise.exerciseGroup = exerciseGroup;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should be in exam mode after onInit', () => {
            // GIVEN
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(exerciseGroupService.find).toHaveBeenCalledWith(courseId, examId, exerciseGroupId);
            expect(comp.isSaving).toBe(false);
            expect(comp.programmingExercise).toStrictEqual(expectedExamProgrammingExercise);
            expect(comp.isExamMode).toBe(true);
        });
    });

    describe('course mode', () => {
        const expectedProgrammingExercise = new ProgrammingExercise(undefined, undefined);
        expectedProgrammingExercise.course = course;

        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
        });

        it('should not be in exam mode after onInit', () => {
            // GIVEN
            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.isSaving).toBe(false);
            expect(comp.programmingExercise).toStrictEqual(expectedProgrammingExercise);
            expect(comp.isExamMode).toBe(false);
        });
    });

    describe('default programming language', () => {
        it.each([true, false])('should set default programming language', (isExamExercise: boolean) => {
            // SETUP
            const route = TestBed.inject(ActivatedRoute);
            route.url = of([{ path: 'new' } as UrlSegment]);
            if (isExamExercise) {
                const examId = 1;
                const exerciseGroupId = 1;
                const exerciseGroup = new ExerciseGroup();
                exerciseGroup.id = exerciseGroupId;
                exerciseGroup.exam = { id: examId, course };
                route.params = of({ courseId, examId, exerciseGroupId });
                route.data = of({ programmingExercise: new ProgrammingExercise(undefined, exerciseGroup) });
                vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            } else {
                route.params = of({ courseId });
                route.data = of({ programmingExercise: new ProgrammingExercise(course, undefined) });
            }
            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            // GIVEN
            const testProgrammingLanguage = ProgrammingLanguage.SWIFT;
            expect(new ProgrammingExercise(undefined, undefined).programmingLanguage).not.toBe(testProgrammingLanguage);
            course.defaultProgrammingLanguage = testProgrammingLanguage;
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(testProgrammingLanguage));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(comp.programmingExercise.programmingLanguage).toBe(testProgrammingLanguage);
        });
    });

    describe('programming language change and features', () => {
        beforeEach(() => {
            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });
            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseFeatureService, 'supportsProgrammingLanguage').mockReturnValue(true);

            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
        });

        it('should reset sca settings if new programming language does not support sca', () => {
            comp.ngOnInit();
            fixture.detectChanges();

            // Activate sca
            comp.programmingExercise.staticCodeAnalysisEnabled = true;

            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 50;

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(true);
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(50);

            // Switch to another programming language not supporting sca
            comp.onProgrammingLanguageChange(ProgrammingLanguage.HASKELL);
            fixture.changeDetectorRef.detectChanges();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(false);
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.HASKELL);
        });

        it('should activate SCA for Swift', () => {
            // WHEN
            fixture.detectChanges();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.SWIFT);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.SWIFT);
            expect(comp.staticCodeAnalysisAllowed).toBe(true);
            expect(comp.packageNamePattern).toBe(APP_NAME_PATTERN_FOR_SWIFT);
        });

        it('should activate SCA for C', () => {
            // WHEN
            fixture.detectChanges();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.GCC);

            // THEN
            expect(courseService.find).toHaveBeenCalledWith(courseId);
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.GCC);
            expect(comp.staticCodeAnalysisAllowed).toBe(true);
        });

        it('should activate SCA for Java', () => {
            // WHEN
            fixture.detectChanges();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.JAVA);
            expect(comp.staticCodeAnalysisAllowed).toBe(true);
            expect(comp.packageNamePattern).toBe(PACKAGE_NAME_PATTERN_FOR_JAVA_KOTLIN);
        });

        it('should deactivate SCA for C (FACT)', () => {
            // WHEN
            fixture.detectChanges();
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.selectedProgrammingLanguage).toBe(ProgrammingLanguage.C);
            expect(comp.selectedProjectType).toBe(ProjectType.FACT);
            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(false);
            expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
        });

        it('should clear custom build definition on programming language change', () => {
            // WHEN
            fixture.detectChanges();
            comp.programmingExercise.buildConfig!.buildPlanConfiguration = 'some custom build definition';
            comp.onProgrammingLanguageChange(ProgrammingLanguage.C);
            comp.onProjectTypeChange(ProjectType.FACT);

            // THEN
            expect(comp.programmingExercise.buildConfig?.buildPlanConfiguration).toBeUndefined();
        });

        // From-scratch by default for AI: the language readme is a worked sample (the Java sorting exercise). If it were pre-filled on the AI create path it would be persisted and
        // read by the server as an authoritative spec ("spec mode"), making the agent rebuild the sample instead of authoring from the instructor's brief. So the AI path must start
        // the statement EMPTY; the manual path keeps the readme starter.
        it('should NOT load the readme template and leave the problem statement empty on the AI create path', () => {
            const getTemplateFile = vi.spyOn(fileService, 'getTemplateFile').mockReturnValue(of('# A worked sorting sample readme that is long enough to look like a real spec'));
            fixture.detectChanges(); // ngOnInit assigns programmingExercise from the route (create mode, id undefined)
            comp.hyperionEnabled = true; // create + Hyperion + not import → showGenerateWithAi() is true
            comp.isImportFromExistingExercise = false;
            comp.isImportFromFile = false;
            comp.isImportFromSharing = false;
            expect(comp.showGenerateWithAi()).toBe(true);
            comp.editMode.set('ai'); // the from-scratch (empty statement) behaviour is gated on AI mode, not merely eligibility

            // Ignore any template load triggered by ngOnInit before AI mode was entered; assert only the language-change behaviour on the AI path.
            getTemplateFile.mockClear();
            comp.programmingExercise.problemStatement = '# stale readme that must be cleared';
            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            expect(getTemplateFile).not.toHaveBeenCalled();
            expect(comp.programmingExercise.problemStatement).toBe('');
            expect(comp.problemStatementLoaded).toBe(true);
        });

        it('should load the readme template on the manual (non-AI) create path', () => {
            const getTemplateFile = vi.spyOn(fileService, 'getTemplateFile').mockReturnValue(of('# Default readme'));
            fixture.detectChanges();
            comp.hyperionEnabled = false; // not AI-eligible → keep today's readme starter
            expect(comp.showGenerateWithAi()).toBe(false);

            comp.onProgrammingLanguageChange(ProgrammingLanguage.JAVA);

            expect(getTemplateFile).toHaveBeenCalled();
            expect(comp.programmingExercise.problemStatement).toBe('# Default readme');
            expect(comp.problemStatementLoaded).toBe(true);
        });
    });

    describe('import with static code analysis', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'import' } as UrlSegment]);
        });

        it('should correctly import into an exam exercise', () => {
            const examId = 1;
            const exerciseGroupId = 1;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };
            const programmingExercise = new ProgrammingExercise(undefined, exerciseGroup);
            vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId, examId, exerciseGroupId });
            route.data = of({ programmingExercise });

            comp.ngOnInit();
            expect(comp.programmingExercise.exerciseGroup).toBe(exerciseGroup);
            expect(comp.programmingExercise.course).toBeUndefined();
            expect(comp.isImportFromExistingExercise).toBe(true);
            expect(comp.isExamMode).toBe(true);
            expect(comp.importOptions.setTestCaseVisibilityToAfterDueDate).toBe(true);
        });

        it('should reset dates, id and project key', () => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            expect(comp.isImportFromFile).toBe(false);
            expect(comp.isImportFromExistingExercise).toBe(true);
            expect(comp.importOptions.setTestCaseVisibilityToAfterDueDate).toBe(false);

            verifyImport(programmingExercise);
        });

        it.each([
            [true, 80, ProjectType.PLAIN_MAVEN],
            [false, undefined, ProjectType.PLAIN_GRADLE],
        ])(
            'should activate recreate build plans and update template when sca changes',
            (scaActivatedOriginal: boolean, maxPenalty: number | undefined, projectType: ProjectType) => {
                const newMaxPenalty = 50;
                const programmingExercise = new ProgrammingExercise(undefined, undefined);
                programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
                programmingExercise.projectType = projectType;
                programmingExercise.staticCodeAnalysisEnabled = scaActivatedOriginal;
                programmingExercise.maxStaticCodeAnalysisPenalty = maxPenalty;
                route.data = of({ programmingExercise });
                comp.ngOnInit();
                fixture.detectChanges();

                expect(comp.isImportFromExistingExercise).toBe(true);
                expect(comp.originalStaticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(maxPenalty);
                expect(comp.programmingExercise).toBe(programmingExercise);
                expect(courseService.find).toHaveBeenCalledWith(courseId);

                // Only available for Maven
                if (projectType === ProjectType.PLAIN_MAVEN) {
                    // Needed to trigger setting of update template since we can't use UI components.
                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();
                    fixture.changeDetectorRef.detectChanges();

                    expect(comp.importOptions.updateTemplate).toBe(true);

                    comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                    comp.onStaticCodeAnalysisChanged();
                    fixture.changeDetectorRef.detectChanges();
                }

                comp.programmingExercise.staticCodeAnalysisEnabled = !scaActivatedOriginal;
                comp.onStaticCodeAnalysisChanged();
                fixture.changeDetectorRef.detectChanges();

                if (!scaActivatedOriginal) {
                    comp.programmingExercise.maxStaticCodeAnalysisPenalty = newMaxPenalty;
                }

                // Recreate build plan and template update should be automatically selected
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(!scaActivatedOriginal);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBe(scaActivatedOriginal ? undefined : newMaxPenalty);
                expect(comp.importOptions.recreateBuildPlans).toBe(true);
                expect(comp.importOptions.updateTemplate).toBe(true);

                comp.importOptions.recreateBuildPlans = !comp.importOptions.recreateBuildPlans;
                comp.onRecreateBuildPlanOrUpdateTemplateChange();

                // SCA should revert to the state of the original exercise, maxPenalty will revert to undefined
                expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(comp.originalStaticCodeAnalysisEnabled);
                expect(comp.programmingExercise.maxStaticCodeAnalysisPenalty).toBeUndefined();
            },
        );

        it('should load exercise categories on import', () => {
            const programmingExercise = getProgrammingExerciseForImport();
            route.data = of({ programmingExercise });
            const loadExerciseCategoriesSpy = vi.spyOn(Utils, 'loadCourseExerciseCategories');

            comp.ngOnInit();

            expect(loadExerciseCategoriesSpy).toHaveBeenCalledOnce();
        });

        // Ensures that exerciseCategories are synchronized in course-mode imports
        it('should sync exerciseCategories from programmingExercise during import', () => {
            // GIVEN
            const categories = [new ExerciseCategory(undefined, undefined)];
            const programmingExercise = getProgrammingExerciseForImport();
            (programmingExercise as any).categories = categories;
            route.data = of({ programmingExercise });

            const findSpy = vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(findSpy).toHaveBeenCalledWith(course.id);
            expect(comp.isExamMode).toBe(false);
            expect(comp.exerciseCategories).toBe(categories);
            expect(comp.importOptions.setTestCaseVisibilityToAfterDueDate).toBe(false);
        });

        // Ensures that exerciseCategories remain empty in exam-mode imports.
        it('should NOT sync exerciseCategories from programmingExercise when importing into an exam', () => {
            // GIVEN
            const examId = 1;
            const exerciseGroupId = 42;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };

            const categories = [new ExerciseCategory(undefined, undefined)];
            const programmingExercise = getProgrammingExerciseForImport();
            (programmingExercise as any).categories = categories;

            route.params = of({ courseId, examId, exerciseGroupId });
            route.data = of({ programmingExercise });
            route.url = of([{ path: 'import' } as UrlSegment]);

            // mock exerciseGroupService
            const findSpy = vi.spyOn(exerciseGroupService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseGroup })));
            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(findSpy).toHaveBeenCalledWith(courseId, examId, exerciseGroupId);
            expect(comp.isExamMode).toBe(true);
            expect(comp.importOptions.setTestCaseVisibilityToAfterDueDate).toBe(true);

            expect(comp.exerciseCategories).toEqual([]);
        });
    });

    describe('import from file', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });

            route.url = of([{ path: 'import-from-file' } as UrlSegment]);
        });

        it('should reset dates, id, project key and store zipFile', () => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.ngOnInit();
            fixture.changeDetectorRef.detectChanges();
            expect(comp.isImportFromFile).toBe(true);
            expect(comp.isImportFromExistingExercise).toBe(false);

            verifyImport(programmingExercise);
        });

        it('should call import-from-file from service on import for entity from file', () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.zipFileForImport = new File([''], 'test.zip');
            comp.isImportFromFile = true;
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            vi.spyOn(programmingExerciseService, 'importFromFile').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseService.importFromFile).toHaveBeenCalledWith(entity, 1);
            expect(comp.isSaving).toBe(false);
        });
    });

    describe('import from sharing', () => {
        let route: ActivatedRoute;

        beforeEach(() => {
            route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });

            route.url = of([{ path: 'import-from-sharing' } as UrlSegment]);
            route.queryParams = of({
                basketToken: 'test-basket-token',
                returnURL: '/test-return-url',
                apiBaseURL: 'https://api.example.com',
                selectedExercise: 5,
                checksum: 'test-checksum',
            });
        });

        it('should import and reset dates, id, project key and handle exercise', () => {
            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = vi.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.changeDetectorRef.detectChanges();
            expect(comp.isImportFromFile).toBe(false);
            expect(comp.isImportFromSharing).toBe(true);
            expect(comp.isImportFromExistingExercise).toBe(false);

            verifyImport(programmingExercise);
        });

        it('should also handle exam mode import', () => {
            const examId = 1;
            const exerciseGroupId = 1;
            const exerciseGroup = new ExerciseGroup();
            exerciseGroup.id = exerciseGroupId;
            exerciseGroup.exam = { id: examId, course };
            route.params = of({ courseId, examId, exerciseGroupId });

            const programmingExercise = getProgrammingExerciseForImport();

            route.data = of({ programmingExercise });
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = vi.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.changeDetectorRef.detectChanges();
            expect(comp.isImportFromFile).toBe(false);
            expect(comp.isImportFromSharing).toBe(true);
            expect(comp.isImportFromExistingExercise).toBe(false);

            verifyImport(programmingExercise);
        });

        it('should call create service on save for new sharing entity', () => {
            // GIVEN
            const entity: ProgrammingExercise = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date
            vi.spyOn(programmingExerciseService, 'automaticSetup').mockReturnValue(
                of(
                    new HttpResponse({
                        body: {
                            ...entity,
                            id: 2,
                        },
                    }),
                ),
            );
            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseSharingService, 'setUpFromSharingImport').mockReturnValue(of(new HttpResponse({ body: entity })));

            comp.isImportFromSharing = true;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseSharingService.setUpFromSharingImport).toHaveBeenCalledWith(comp.programmingExercise, course.id!, comp['sharingInfo']);
        });

        it('should call create service on save for new sharing entity, but save failed', () => {
            // GIVEN
            const entity = new ProgrammingExercise(undefined, undefined);
            entity.releaseDate = dayjs(); // We will get a warning if we do not set a release date

            vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
            vi.spyOn(programmingExerciseSharingService, 'setUpFromSharingImport').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'save failed', status: 503 })));

            comp.isImportFromSharing = true;

            comp.programmingExercise = entity;
            comp.backupExercise = {} as ProgrammingExercise;
            comp.programmingExercise.course = course;
            comp.courseId = course.id!;
            // WHEN
            comp.save();

            // THEN
            expect(programmingExerciseSharingService.setUpFromSharingImport).toHaveBeenCalledWith(comp.programmingExercise, course.id!, comp['sharingInfo']);
        });

        it('should import without buildConfig and reset dates, id, project key and store zipFile', () => {
            const programmingExercise = getProgrammingExerciseForImport();
            programmingExercise.buildConfig = undefined; // Simulate no build config

            route.data = of({ programmingExercise });
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            const sharingServiceStub = vi.spyOn(programmingExerciseSharingService, 'loadDetailsForExercises');
            sharingServiceStub.mockReturnValue(of(programmingExercise));
            comp.ngOnInit();

            fixture.changeDetectorRef.detectChanges();
            expect(comp.isImportFromFile).toBe(false);
            expect(comp.isImportFromSharing).toBe(true);
            expect(comp.isImportFromExistingExercise).toBe(false);

            verifyImport(programmingExercise);
        });
    });

    describe('input error validation', () => {
        beforeEach(() => {
            // GIVEN
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
        });

        it('find validation errors for undefined input values', () => {
            // invalid input
            comp.programmingExercise.title = undefined;
            comp.programmingExercise.shortName = undefined;
            comp.programmingExercise.maxPoints = undefined;
            comp.programmingExercise.bonusPoints = undefined;
            comp.programmingExercise.packageName = undefined;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toHaveLength(5);
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for empty input strings', () => {
            // invalid input
            comp.programmingExercise.title = '';
            comp.programmingExercise.shortName = '';
            comp.programmingExercise.packageName = '';

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.undefined',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for input values not matching the pattern', () => {
            comp.programmingExercise.title = '%§"$"§';
            comp.programmingExercise.shortName = '123';
            comp.programmingExercise.maxPoints = 0;
            comp.programmingExercise.bonusPoints = -1;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = -1;

            const reasons: any[] = comp.getInvalidReasons();
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.title.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.pattern',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.bonusPoints.customMin',
                translateValues: {},
            });
            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.exercise.form.maxPenalty.pattern',
                translateValues: {},
            });
        });

        it('find validation errors for package name not matching the pattern', () => {
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.SWIFT;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.SWIFT',
                translateValues: {},
            });

            comp.programmingExercise.packageName = 'de/';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.JAVA',
                translateValues: {},
            });

            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.KOTLIN;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.pattern.KOTLIN',
                translateValues: {},
            });
        });

        it('validateExerciseChannelName', () => {
            comp.programmingExercise.channelName = '';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.channelName.empty',
                translateValues: {},
            });
        });

        it('validateExerciseShortName', () => {
            comp.programmingExercise.shortName = 'ab';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.shortName.minlength',
                translateValues: {},
            });
        });

        it('validateExercisePoints', () => {
            comp.programmingExercise.maxPoints = 10_000;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMax',
                translateValues: {},
            });
        });

        it('validateExerciseBonusPoints', () => {
            comp.programmingExercise.maxPoints = 10_000;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.exercise.form.points.customMax',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language C', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.C;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Empty', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.EMPTY;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Python', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.PYTHON;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language Assembler', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.ASSEMBLER;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language OCAML', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.OCAML;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('Check that no package name related validation error occurs for language VHDL', () => {
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.VHDL;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.exercise.form.packageName.undefined',
                translateValues: {},
            });
        });

        it('find validation errors for invalid auxiliary repositories', () => {
            comp.auxiliaryRepositoriesValid.set(false);
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.auxiliaryRepository.error',
                translateValues: {},
            });
        });

        it.each([
            [
                'find validation errors for invalid ide selection',
                {
                    activeModuleFeatures: [MODULE_FEATURE_THEIA],
                },
                {
                    translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alert',
                    translateValues: {},
                },
            ],
            [
                'find validation errors for invalid ide selection without theia module feature',
                {
                    activeModuleFeatures: [],
                },
                {
                    translateKey: 'artemisApp.programmingExercise.allowOnlineEditor.alertNoTheia',
                    translateValues: {},
                },
            ],
        ])('%s', (description: string, profileInfo: Partial<ProfileInfo>, expectedException: ValidationReason) => {
            const newProfileInfo = new ProfileInfo();
            newProfileInfo.activeProfiles = profileInfo.activeProfiles ?? [];
            newProfileInfo.activeModuleFeatures = profileInfo.activeModuleFeatures ?? [];
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(newProfileInfo);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature: string) => {
                return newProfileInfo.activeModuleFeatures?.includes(feature) ?? false;
            });

            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'new' } as UrlSegment]);
            route.data = of({ programmingExercise: comp.programmingExercise });

            vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.allowOfflineIde = false;
            comp.programmingExercise.allowOnlineIde = false;

            fixture.changeDetectorRef.detectChanges();

            expect(comp.getInvalidReasons()).toContainEqual(expectedException);
        });

        it('find validation errors for invalid online IDE image', () => {
            comp.programmingExercise.allowOnlineIde = true;
            comp.programmingExercise.buildConfig!.theiaImage = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.theiaImage.alert',
                translateValues: {},
            });
        });

        it('should update AuxiliaryRepository checkout directory', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateCheckoutDirectory(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.checkoutDirectory).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should update AuxiliaryRepository name', () => {
            const auxiliaryRepository = new AuxiliaryRepository();
            auxiliaryRepository.checkoutDirectory = 'aux';
            auxiliaryRepository.name = 'aux';
            auxiliaryRepository.repositoryUri = 'auxurl';
            comp.programmingExercise.auxiliaryRepositories = [auxiliaryRepository];
            const returned = comp.updateRepositoryName(auxiliaryRepository)('new-value');
            expect(auxiliaryRepository.name).toBe('new-value');
            expect(returned).toBe('new-value');
        });

        it('should find no validation errors for valid input', () => {
            comp.programmingExercise.title = 'New title';
            comp.programmingExercise.shortName = 'home2';
            comp.programmingExercise.maxPoints = 10;
            comp.programmingExercise.bonusPoints = 0;
            comp.programmingExercise.staticCodeAnalysisEnabled = true;
            comp.programmingExercise.maxStaticCodeAnalysisPenalty = 60;
            comp.programmingExercise.allowOfflineIde = true;
            comp.programmingExercise.allowOnlineEditor = false;
            comp.programmingExercise.allowOnlineIde = false;
            comp.programmingExercise.packageName = 'de.tum.in';
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            expect(comp.getInvalidReasons()).toEqual([]);
        });

        it('should find validation errors for invalid submission limit value', () => {
            comp.programmingExercise.submissionPolicy = new LockRepositoryPolicy();
            comp.programmingExercise.submissionPolicy.submissionLimit = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.required',
                translateValues: {},
            });

            const patternViolatingValues = [0, 501, 30.3];
            for (const value of patternViolatingValues) {
                comp.programmingExercise.submissionPolicy.submissionLimit = value;
                expect(comp.getInvalidReasons()).toContainEqual({
                    translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionLimitWarning.pattern',
                    translateValues: {},
                });
            }
        });

        it('should find validation errors invalid submission exceeding penalty', () => {
            comp.programmingExercise.submissionPolicy = new SubmissionPenaltyPolicy();

            comp.programmingExercise.submissionPolicy.exceedingPenalty = undefined;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.required',
                translateValues: {},
            });

            comp.programmingExercise.submissionPolicy.exceedingPenalty = 0;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.penaltyInputFieldValidationWarning.pattern',
                translateValues: {},
            });
        });

        it('should find no package name related validation error for languages that do not need a package name', () => {
            for (const programmingLanguage of [ProgrammingLanguage.C, ProgrammingLanguage.EMPTY, ProgrammingLanguage.PYTHON]) {
                comp.programmingExercise.programmingLanguage = programmingLanguage;
                expect(comp.getInvalidReasons()).not.toContainEqual({
                    translateKey: 'artemisApp.exercise.form.packageName.undefined',
                    translateValues: {},
                });
            }
        });

        it('should find invalid timeout', () => {
            comp.programmingExercise.buildConfig!.timeoutSeconds = -1;
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.timeout.alert',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.timeoutSeconds = 100;
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.timeout.alert',
                translateValues: {},
            });
        });

        it('should find invalid checkoutpaths', () => {
            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment';
            comp.programmingExercise.buildConfig!.testCheckoutPath = 'assignment';
            comp.programmingExercise.buildConfig!.solutionCheckoutPath = 'solution';

            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.testCheckoutPath = 'test';
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment/';
            expect(comp.getInvalidReasons()).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });

            comp.programmingExercise.buildConfig!.assignmentCheckoutPath = 'assignment';
            expect(comp.getInvalidReasons()).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.checkoutPath.invalid',
                translateValues: {},
            });
        });

        it('should add validation error when problem statement exceeds max length', () => {
            comp.programmingExercise.problemStatement = 'a'.repeat(MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH + 1);

            const reasons = comp.getInvalidReasons();

            expect(reasons).toContainEqual({
                translateKey: 'artemisApp.programmingExercise.problemStatement.tooLong',
                translateValues: { max: MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH },
            });
        });

        it('should not add validation error when problem statement is within max length', () => {
            comp.programmingExercise.problemStatement = 'a'.repeat(MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH);

            const reasons = comp.getInvalidReasons();

            expect(reasons).not.toContainEqual({
                translateKey: 'artemisApp.programmingExercise.problemStatement.tooLong',
                translateValues: { max: MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH },
            });
        });
    });

    describe('disable features based on selected language and project type', () => {
        beforeEach(() => {
            const entity = new ProgrammingExercise(new Course(), undefined);
            entity.id = 123;
            entity.channelName = 'notificationText';
            comp.programmingExercise = entity;
            comp.programmingExercise.course = course;
            comp.programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;

            const route = TestBed.inject(ActivatedRoute);
            route.params = of({ courseId });
            route.url = of([{ path: 'edit' } as UrlSegment]);
            route.data = of({ programmingExercise: comp.programmingExercise });
        });

        describe('should initialize isEdit variable based on url', () => {
            it('should be false if edit is in url', () => {
                vi.spyOn(comp, 'selectedProgrammingLanguage', 'set').mockImplementation(() => {});

                fixture.detectChanges();

                expect(comp.isEdit).toBe(true);
            });

            it('should be true if edit is in url', () => {
                vi.spyOn(comp, 'selectedProgrammingLanguage', 'set').mockImplementation(() => {});
                const route = TestBed.inject(ActivatedRoute);
                route.params = of({ courseId });
                route.url = of([{ path: 'notEdit' } as UrlSegment]);
                route.data = of({ programmingExercise: comp.programmingExercise });

                fixture.detectChanges();

                expect(comp.isEdit).toBe(false);
            });
        });

        it('should disable checkboxes for certain options of existing exercise', () => {
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

            fixture.detectChanges();

            expect(comp.programmingExercise.staticCodeAnalysisEnabled).toBe(false);
        });

        it('should disable options for java dejagnu project type and re-enable them after changing back to maven or gradle', () => {
            const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
            getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));
            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBe(false);

            comp.selectedProjectType = ProjectType.MAVEN_MAVEN;
            expect(comp.sequentialTestRunsAllowed).toBe(true);

            comp.selectedProjectType = ProjectType.MAVEN_BLACKBOX;
            expect(comp.sequentialTestRunsAllowed).toBe(false);

            comp.selectedProjectType = ProjectType.GRADLE_GRADLE;
            expect(comp.sequentialTestRunsAllowed).toBe(true);
        });
    });

    it('should return the exercise creation config', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();

        const problemStepInputs = comp.getProgrammingExerciseCreationConfig();
        expect(problemStepInputs).not.toBeNull();
    });

    it('stores with dependencies when changed', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();

        expect(comp.withDependencies).toBe(false);
        comp.onWithDependenciesChanged(true);
        expect(comp.withDependencies).toBe(true);
    });

    it('stores updated categories', () => {
        const route = TestBed.inject(ActivatedRoute);
        route.params = of({ courseId });
        route.url = of([{ path: 'new' } as UrlSegment]);
        route.data = of({ programmingExercise: new ProgrammingExercise(undefined, undefined) });

        const getFeaturesStub = vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature');
        getFeaturesStub.mockImplementation((language: ProgrammingLanguage) => getProgrammingLanguageFeature(language));

        fixture.detectChanges();

        const categories = [new ExerciseCategory(undefined, undefined)];
        expect(comp.exerciseCategories).toBeUndefined();
        comp.updateCategories(categories);
        expect(comp.exerciseCategories).toBe(categories);
    });

    it('keeps exerciseCategories and programmingExercise.categories in sync when initially empty', () => {
        const route = TestBed.inject(ActivatedRoute);
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.id = 42;
        programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
        programmingExercise.categories = undefined;

        route.params = of({ courseId });
        route.url = of([{ path: 'edit' } as UrlSegment]);
        route.data = of({ programmingExercise });

        vi.spyOn(courseService, 'find').mockReturnValue(of(new HttpResponse({ body: course })));
        vi.spyOn(Utils, 'loadCourseExerciseCategories').mockReturnValue(of([]));
        vi.spyOn(programmingExerciseFeatureService, 'getProgrammingLanguageFeature').mockReturnValue(getProgrammingLanguageFeature(ProgrammingLanguage.JAVA));

        comp.ngOnInit();

        expect(comp.exerciseCategories).toEqual([]);
        expect(comp.programmingExercise.categories).toBe(comp.exerciseCategories);
    });

    it('should mark the problem section as invalid when problem statement exceeds max length', () => {
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.programmingExercise.problemStatement = 'a'.repeat(MAX_PROGRAMMING_EXERCISE_PROBLEM_STATEMENT_LENGTH + 1);

        comp.calculateFormStatusSections();

        const problemSection = comp.formStatusSections().find((section) => section.title === 'artemisApp.programmingExercise.wizardMode.detailedSteps.problemStepTitle');

        expect(problemSection?.valid).toBe(false);
    });

    it('should validate form sections', () => {
        const calculateFormValidSectionsSpy = vi.spyOn(comp, 'calculateFormStatusSections');
        comp.programmingExercise = new ProgrammingExercise(undefined, undefined);
        const exerciseInfoComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseInformationComponent;
        const exerciseDifficultyComponent = {
            teamConfigComponent: {
                formValidChanges: new Subject(),
                formValid: true,
            },
        } as ProgrammingExerciseModeComponent;
        const exerciseLanguageComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseLanguageComponent;
        const exerciseGradingComponent = {
            formValidChanges: new Subject(),
            formValid: true,
        } as ProgrammingExerciseGradingComponent;

        internals(comp).exerciseInfoComponent = signal<ProgrammingExerciseInformationComponent | undefined>(exerciseInfoComponent).asReadonly();
        internals(comp).exerciseDifficultyComponent = signal<ProgrammingExerciseModeComponent | undefined>(exerciseDifficultyComponent).asReadonly();
        internals(comp).exerciseLanguageComponent = signal<ProgrammingExerciseLanguageComponent | undefined>(exerciseLanguageComponent).asReadonly();
        internals(comp).exerciseGradingComponent = signal<ProgrammingExerciseGradingComponent | undefined>(exerciseGradingComponent).asReadonly();
        internals(comp).exercisePlagiarismComponent = signal<ExerciseUpdatePlagiarismComponent | undefined>({
            isFormValid: signal<boolean>(true),
        } as unknown as ExerciseUpdatePlagiarismComponent).asReadonly();

        comp.ngAfterViewInit();
        // we migrate from subscriptions to signals eventually
        expect(comp.inputFieldSubscriptions).toHaveLength(4);
        comp.calculateFormStatusSections();

        for (const section of comp.formStatusSections()) {
            expect(section.valid).toBe(true);
        }

        exerciseInfoComponent.formValid = false;
        (exerciseInfoComponent.formValidChanges as Subject<boolean>).next(false);

        expect(comp.formStatusSections()[0].valid).toBe(false);

        (exerciseLanguageComponent.formValidChanges as Subject<boolean>).next(false);
        (exerciseGradingComponent.formValidChanges as Subject<boolean>).next(false);
        (exerciseDifficultyComponent.teamConfigComponent!.formValidChanges as Subject<boolean>).next(false);

        expect(calculateFormValidSectionsSpy).toHaveBeenCalledTimes(5);

        comp.programmingExercise.allowOfflineIde = false;
        comp.programmingExercise.allowOnlineEditor = false;
        comp.programmingExercise.allowOnlineIde = false;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections()[1].valid).toBe(false);
        comp.programmingExercise.allowOnlineEditor = true;
        comp.calculateFormStatusSections();
        expect(comp.formStatusSections()[1].valid).toBe(true);

        comp.ngOnDestroy();

        for (const subscription of comp.inputFieldSubscriptions) {
            expect(subscription?.closed ?? true).toBe(true);
        }
    });

    function verifyImport(importedProgrammingExercise: ProgrammingExercise) {
        expect(comp.programmingExercise.projectKey).toBeUndefined();
        expect(comp.programmingExercise.id).toBeUndefined();
        expect(comp.programmingExercise.dueDate).toBeUndefined();
        expect(comp.programmingExercise.releaseDate).toBeUndefined();
        expect(comp.programmingExercise.startDate).toBeUndefined();
        expect(comp.programmingExercise.assessmentDueDate).toBeUndefined();
        expect(comp.programmingExercise.exampleSolutionPublicationDate).toBeUndefined();
        expect(comp.programmingExercise.zipFileForImport?.name).toBe('test.zip');
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBe(false);
        expect(comp.programmingExercise.allowOfflineIde).toBe(true);
        expect(comp.programmingExercise.allowOnlineEditor).toBe(true);
        expect(comp.programmingExercise.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(comp.programmingExercise.projectType).toBe(ProjectType.PLAIN_MAVEN);
        // allow manual feedback requests and complaints for automatic assessments should be set to false because we reset all dates and hence they can only be false
        expect(comp.programmingExercise.allowFeedbackRequests).toBe(false);
        expect(comp.programmingExercise.allowComplaintsForAutomaticAssessments).toBe(false);
        // name and short name should also be imported
        expect(comp.programmingExercise.title).toEqual(importedProgrammingExercise.title);
        expect(comp.programmingExercise.shortName).toEqual(importedProgrammingExercise.shortName);
    }
});

const getProgrammingExerciseForImport = () => {
    const programmingExercise = new ProgrammingExercise(undefined, undefined);
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.projectType = ProjectType.PLAIN_MAVEN;
    programmingExercise.dueDate = dayjs();
    programmingExercise.releaseDate = dayjs();
    programmingExercise.startDate = dayjs();
    programmingExercise.projectKey = 'projectKey';

    programmingExercise.assessmentDueDate = dayjs();
    programmingExercise.exampleSolutionPublicationDate = dayjs();
    programmingExercise.programmingLanguage = ProgrammingLanguage.JAVA;
    programmingExercise.zipFileForImport = new File([''], 'test.zip');
    programmingExercise.allowOfflineIde = true;
    programmingExercise.allowOnlineEditor = true;
    programmingExercise.allowComplaintsForAutomaticAssessments = true;
    programmingExercise.allowFeedbackRequests = true;

    history.pushState({ programmingExerciseForImportFromFile: programmingExercise }, '');

    programmingExercise.shortName = 'shortName';
    programmingExercise.title = 'title';

    return programmingExercise;
};

const getProgrammingLanguageFeature = (programmingLanguage: ProgrammingLanguage) => {
    switch (programmingLanguage) {
        case ProgrammingLanguage.SWIFT:
            return {
                programmingLanguage: ProgrammingLanguage.SWIFT,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: false,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: false,
                projectTypes: [ProjectType.PLAIN, ProjectType.XCODE],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.JAVA:
            return {
                programmingLanguage: ProgrammingLanguage.JAVA,
                sequentialTestRuns: true,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: true,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.HASKELL:
            return {
                programmingLanguage: ProgrammingLanguage.HASKELL,
                sequentialTestRuns: false,
                staticCodeAnalysis: false,
                plagiarismCheckSupported: false,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        case ProgrammingLanguage.C:
            return {
                programmingLanguage: ProgrammingLanguage.C,
                sequentialTestRuns: false,
                staticCodeAnalysis: true,
                plagiarismCheckSupported: true,
                packageNameRequired: false,
                checkoutSolutionRepositoryAllowed: true,
                projectTypes: [ProjectType.FACT, ProjectType.GCC],
                auxiliaryRepositoriesSupported: true,
            } as ProgrammingLanguageFeature;
        default:
            throw new Error();
    }
};

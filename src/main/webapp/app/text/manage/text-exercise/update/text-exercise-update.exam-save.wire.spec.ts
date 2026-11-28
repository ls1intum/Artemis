/**
 * Vitest tests for TextExerciseUpdateComponent.
 *
 * vi.mock('monaco-editor') is required because MonacoTextEditorAdapter has static initializers
 * that run before the path alias mock can take effect. vi.mock hoists to file top.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('monaco-editor', () => ({
    editor: { create: () => ({}), createModel: () => ({}), defineTheme: () => {}, setTheme: () => {} },
    languages: { register: () => {}, setMonarchTokensProvider: () => {}, setLanguageConfiguration: () => {}, registerCompletionItemProvider: () => ({ dispose: () => {} }) },
    Range: class {
        constructor(
            public startLineNumber: number,
            public startColumn: number,
            public endLineNumber: number,
            public endColumn: number,
        ) {}
    },
    Position: class {
        constructor(
            public lineNumber: number,
            public column: number,
        ) {}
    },
    KeyCode: { Backspace: 1, Tab: 2, Enter: 3, Escape: 9, Delete: 10 },
    KeyMod: { CtrlCmd: 2048, Shift: 1024, Alt: 512, WinCtrl: 256 },
    MarkerSeverity: { Error: 8, Warning: 4, Info: 2, Hint: 1 },
    Uri: { parse: (s: string) => ({ toString: () => s }) },
}));

import { TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Data, Params, UrlSegment, provideRouter } from '@angular/router';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { provideTranslateService } from '@ngx-translate/core';
import { MockComponent, MockDirective } from 'ng-mocks';
import { TumAetUiDialogService } from '@tumaet/ui-angular';
import { Component, forwardRef, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PointsInputComponent } from 'app/shared-ui/tum-aet-ui-integration/points-input/points-input.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { TextExerciseUpdateComponent } from 'app/text/manage/text-exercise/update/text-exercise-update.component';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { ExerciseMode } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamAssignmentConfig } from 'app/exercise/shared/entities/team/team-assignment-config.model';

import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ExerciseUpdateWarningService } from 'app/exercise/exercise-update-warning/exercise-update-warning.service';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { ActivatedRouteSnapshot } from '@angular/router';
import { HttpTestingController } from '@angular/common/http/testing';
import { CalendarService } from 'app/calendar/shared/service/calendar.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExerciseGroupTimelineLockStubComponent } from 'test/helpers/stubs/exercise/exercise-group-timeline-lock-stub.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { PresentationScoreComponent } from 'app/exercise/presentation-score/presentation-score.component';
import { GradingInstructionsDetailsComponent } from 'app/exercise/structured-grading-criterion/grading-instructions-details/grading-instructions-details.component';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { FormStatusBarComponent } from 'app/shared-ui/form/form-status-bar/form-status-bar.component';
import { FormFooterComponent } from 'app/shared-ui/form/form-footer/form-footer.component';
import { CategorySelectorComponent } from 'app/exercise/category-selector/category-selector.component';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
import { ExerciseTimelineComponent } from 'app/exercise/exercise-timeline/exercise-timeline.component';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { ExerciseUpdatePlagiarismComponent } from 'app/plagiarism/manage/exercise-update-plagiarism/exercise-update-plagiarism.component';
import { ExerciseGroupDateNoticeComponent } from 'app/exercise/exercise-group-date-notice/exercise-group-date-notice.component';

// NOTE: Do NOT import MarkdownEditorMonacoComponent here - it transitively imports monaco-editor
// which causes static initializers to run before mocks are applied.

// Mock component to replace MarkdownEditorMonacoComponent without importing the real one
@Component({ selector: 'jhi-markdown-editor-monaco', template: '', standalone: true })
class MockMarkdownEditorMonacoComponent {
    markdown = input<string>('');
    domainActions = input<unknown[]>([]);
}

class MockTitleChannelNameComponent {
    isValid = signal(true);
    isChannelFieldDisplayed = signal(true);
    isTitleDisallowed = signal(false);
}

// Stub for ExerciseTitleChannelNameComponent - ng-mocks MockComponent doesn't handle viewChild properly.
// Provided under the real class, so that the host's viewChild(ExerciseTitleChannelNameComponent) finds it.
@Component({
    selector: 'jhi-exercise-title-channel-name',
    template: '',
    standalone: true,
    providers: [{ provide: ExerciseTitleChannelNameComponent, useExisting: forwardRef(() => StubExerciseTitleChannelNameComponent) }],
})
class StubExerciseTitleChannelNameComponent {
    exercise = input<TextExercise | undefined>();
    titlePattern = input<string>('');
    minTitleLength = input<number>(0);
    isExamMode = input<boolean>(false);
    isImport = input<boolean>(false);
    hideTitleLabel = input<boolean>(false);
    course = input<Course>();
    isEditFieldDisplayedRecord = input<Record<string, boolean>>();
    courseId = input<number>();
    onTitleChange = output<string>();
    onChannelNameChange = output<string>();
    exerciseChange = output<TextExercise>();
    // Use a method that returns a mock instance instead of viewChild
    private readonly _titleChannelNameComponent = new MockTitleChannelNameComponent();
    titleChannelNameComponent = () => this._titleChannelNameComponent;
}

// Stub for ExerciseUpdatePlagiarismComponent, provided under the real class for the host's viewChild
@Component({
    selector: 'jhi-exercise-update-plagiarism',
    template: '',
    standalone: true,
    providers: [{ provide: ExerciseUpdatePlagiarismComponent, useExisting: forwardRef(() => StubExerciseUpdatePlagiarismComponent) }],
})
class StubExerciseUpdatePlagiarismComponent {
    exercise = input<TextExercise | undefined>();
    isFormValid = signal(true);
    invalidControlNames = signal<string[]>([]);
}

// Stub for TeamConfigFormGroupComponent
@Component({ selector: 'jhi-team-config-form-group', template: '', standalone: true })
class StubTeamConfigFormGroupComponent {
    exercise = input.required<TextExercise>();
    isImport = input<boolean>(false);
    exerciseChange = output<TextExercise>();

    /** Changes the team config the way the real component does: it writes onto the exercise, then emits it. */
    changeTeamConfig(mode: ExerciseMode, minTeamSize?: number) {
        const exercise = this.exercise();
        exercise.mode = mode;
        exercise.teamAssignmentConfig = undefined;
        if (mode === ExerciseMode.TEAM) {
            exercise.teamAssignmentConfig = new TeamAssignmentConfig();
            exercise.teamAssignmentConfig.minTeamSize = minTeamSize;
            exercise.teamAssignmentConfig.maxTeamSize = 5;
        }
        this.exerciseChange.emit(exercise);
    }
}

/**
 * Wire contract of an exam text exercise editor that loads its exercise group through ExerciseGroupService.find: the
 * same group JSON must give the same exercise state, and the same editor state must send the same save request.
 * The group fixture carries every field of ExerciseGroupDTO of a single-group response.
 */
describe('TextExercise exam save wire contract', () => {
    let httpMock: HttpTestingController;
    let routeData$: BehaviorSubject<Data>;
    let routeUrl$: BehaviorSubject<UrlSegment[]>;
    let routeParams$: BehaviorSubject<Params>;

    const groupJson = {
        id: 3,
        title: 'Group 1',
        isMandatory: true,
        exam: {
            id: 2,
            examMode: 'REAL',
            exampleSolutionPublicationDate: '2026-03-04T10:15:30.123Z',
            course: { id: 1, defaultProgrammingLanguage: 'JAVA' },
        },
    };

    beforeEach(async () => {
        routeData$ = new BehaviorSubject<Data>({});
        routeUrl$ = new BehaviorSubject([{ path: 'new' }] as UrlSegment[]);
        routeParams$ = new BehaviorSubject<Params>({ courseId: 1, examId: 2, exerciseGroupId: 3 });

        await TestBed.configureTestingModule({
            imports: [TextExerciseUpdateComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: { data: routeData$.asObservable(), url: routeUrl$.asObservable(), params: routeParams$.asObservable(), snapshot: { paramMap: { get: () => null } } },
                },
                { provide: TumAetUiDialogService, useValue: { open: vi.fn(), hasOpenDialogs: vi.fn().mockReturnValue(false) } },
                {
                    provide: CourseManagementService,
                    useValue: { findForManagement: vi.fn(), findAllCategoriesOfCourse: vi.fn().mockReturnValue(of(new HttpResponse({ body: [] }))) },
                },
                { provide: ExerciseService, useValue: { validateDate: vi.fn(), convertExerciseCategoriesAsStringFromServer: vi.fn().mockReturnValue([]) } },
                { provide: ArtemisNavigationUtilService, useValue: { navigateBackFromExerciseUpdate: vi.fn(), navigateForwardFromExerciseUpdateOrCreation: vi.fn() } },
                {
                    provide: ExerciseUpdateWarningService,
                    useValue: { checkForExerciseUpdateWarning: vi.fn().mockReturnValue(of(undefined)), checkExerciseBeforeUpdate: vi.fn().mockResolvedValue(undefined) },
                },
                { provide: CalendarService, useValue: { reloadEvents: vi.fn() } },
                { provide: ProfileService, useClass: MockProfileService },
                provideTranslateService(),
            ],
        })
            .overrideComponent(TextExerciseUpdateComponent, {
                set: {
                    imports: [
                        FormsModule,
                        PointsInputComponent,
                        MockDirective(TranslateDirective),
                        FaIconComponent,
                        ArtemisTranslatePipe,
                        StubExerciseTitleChannelNameComponent,
                        StubTeamConfigFormGroupComponent,
                        MockComponent(IncludedInOverallScorePickerComponent),
                        MockComponent(PresentationScoreComponent),
                        MockComponent(GradingInstructionsDetailsComponent),
                        MockComponent(DocumentationButtonComponent),
                        MockComponent(FormStatusBarComponent),
                        MockComponent(FormFooterComponent),
                        MockComponent(CategorySelectorComponent),
                        MockComponent(DifficultyPickerComponent),
                        MockComponent(HelpIconComponent),
                        MockComponent(CompetencySelectionComponent),
                        MockMarkdownEditorMonacoComponent,
                        StubExerciseUpdatePlagiarismComponent,
                        MockComponent(FeatureOverlayComponent),
                        ExerciseGroupTimelineLockStubComponent,
                        ExerciseTimelineComponent,
                        MockComponent(ExerciseGroupDateNoticeComponent),
                    ],
                },
            })
            .compileComponents();
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        vi.clearAllMocks();
    });

    const expectGroupRequest = () => httpMock.expectOne((req) => req.method === 'GET' && req.url.replace(/^\//, '') === 'api/exam/courses/1/exams/2/exercise-groups/3');

    it('should resolve the group of a new exam exercise, save it and send the group by id', async () => {
        let exercise: TextExercise | undefined;
        const route = { params: { courseId: '1', examId: '2', exerciseGroupId: '3' } } as unknown as ActivatedRouteSnapshot;
        (TestBed.inject(TextExerciseResolver).resolve(route) as Observable<TextExercise>).subscribe((resolved) => (exercise = resolved));
        expectGroupRequest().flush(groupJson);

        expect(exercise?.exerciseGroup).toEqual(groupJson);
        exercise!.title = 'Exam text exercise';
        exercise!.channelName = 'exam-text';
        exercise!.maxPoints = 10;

        routeData$.next({ textExercise: exercise });
        routeUrl$.next([{ path: 'exercise-groups' }, { path: 'text-exercises' }, { path: 'new' }] as UrlSegment[]);
        const fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.componentInstance.save();
        await fixture.whenStable();

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.url.replace(/^\//, '')).toBe('api/text/text-exercises');
        expect(req.request.params.keys()).toEqual([]);
        expect(req.request.body).toEqual({
            allowComplaintsForAutomaticAssessments: false,
            bonusPoints: 0,
            channelName: 'exam-text',
            competencyLinks: [],
            exerciseGroupId: 3,
            includedInOverallScore: 'INCLUDED_COMPLETELY',
            maxPoints: 10,
            mode: 'INDIVIDUAL',
            plagiarismDetectionConfig: {
                continuousPlagiarismControlEnabled: false,
                continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod: 7,
                continuousPlagiarismControlPostDueDateChecksEnabled: false,
                minimumScore: 0,
                minimumSize: 50,
                similarityThreshold: 90,
            },
            presentationScoreEnabled: false,
            secondCorrectionEnabled: false,
            title: 'Exam text exercise',
        });
        req.flush({ id: 77 });
        await fixture.whenStable();
    });

    it('should load the group of the import target through the exam route and keep it as sent', async () => {
        const source = new TextExercise(undefined, undefined);
        source.id = 55;
        source.title = 'Source';
        source.channelName = 'source';
        routeData$.next({ textExercise: source });
        routeUrl$.next([{ path: 'exercise-groups' }, { path: 'text-exercises' }, { path: 'import' }, { path: '55' }] as UrlSegment[]);
        const fixture = TestBed.createComponent(TextExerciseUpdateComponent);
        fixture.detectChanges();

        expectGroupRequest().flush(groupJson);
        await fixture.whenStable();

        expect(fixture.componentInstance.textExercise.exerciseGroup).toEqual(groupJson);
        expect(fixture.componentInstance.textExercise.course).toBeUndefined();
    });
});

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TemplateRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, ngMocks } from 'ng-mocks';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseScoresComponent, FilterProp } from 'app/exercise/exercise-scores/exercise-scores.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Range } from 'app/foundation/util/utils';
import { ParticipationNameExportDTO } from 'app/exercise/exercise-scores/participation-name-export-dto.model';
import { Subscription, of } from 'rxjs';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockCourseManagementService } from 'test/helpers/mocks/service/mock-course-management.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ParticipationScoreDTO } from 'app/exercise/exercise-scores/participation-score-dto.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { IrisVerdict } from 'app/iris/shared/entities/iris-verdict.model';
import { IrisSettingsService } from 'app/iris/manage/settings/shared/iris-settings.service';
import { FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { AccountService } from 'app/core/auth/account.service';
import { TableViewComponent } from 'app/shared-ui/table-view/table-view';
import { CodeButtonComponent } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { IrisReviewAssessmentButtonComponent } from 'app/iris/overview/ask-user/shared/iris-assessment-button/iris-review-assessment-button.component';
import { ManageAssessmentButtonsComponent } from 'app/exercise/exercise-scores/manage-assessment-buttons/manage-assessment-buttons.component';

const MockTableViewComponent = MockComponent(TableViewComponent);

describe('Exercise Scores Component', () => {
    let component: ExerciseScoresComponent;
    let fixture: ComponentFixture<ExerciseScoresComponent>;
    let resultService: ResultService;
    let participationService: ParticipationService;
    let programmingSubmissionService: ProgrammingSubmissionService;
    let courseService: CourseManagementService;
    let exerciseService: ExerciseService;
    let profileService: ProfileService;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const course = new Course();
    course.id = 1;

    const sampleDto: ParticipationScoreDTO = {
        participationId: 1,
        submissionCount: 2,
        participantName: 'participantName',
        participantIdentifier: 'participationId',
        score: 80,
        successful: false,
        assessmentType: AssessmentType.MANUAL,
        durationInSeconds: 120,
        buildPlanId: '1',
        repositoryUri: 'url',
        testRun: false,
        testCaseCount: 10,
        passedTestCaseCount: 5,
        codeIssueCount: 0,
    };

    const scoresToFilter = [3, 11, 22, 33, 44, 55, 66, 77, 88, 100];
    let dtosToFilter: ParticipationScoreDTO[];

    const route = {
        data: of({ courseId: 1 }),
        children: [],
        params: of({ courseId: 1, exerciseId: 2 }),
        snapshot: { queryParamMap: { get: () => undefined } },
    } as any as ActivatedRoute;

    beforeAll(() => {
        dtosToFilter = scoresToFilter.map((score, index) => ({
            participationId: index + 1,
            submissionCount: 1,
            participantName: `student${index}`,
            participantIdentifier: `login${index}`,
            score,
            successful: score >= 100,
            durationInSeconds: 60,
            testRun: false,
        }));
    });

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: route },
                { provide: ResultService, useClass: MockResultService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: CourseManagementService, useClass: MockCourseManagementService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: IrisSettingsService, useValue: { getCourseSettingsWithRateLimit: () => of({ settings: { askUserModeEnabled: false } }) } },
                { provide: FeatureToggleService, useValue: { getFeatureToggleActive: () => of(true) } },
            ],
        });

        // Mock the row actions column's heavy child components so the actionsTemplate can be rendered in isolation.
        TestBed.overrideComponent(ExerciseScoresComponent, {
            remove: { imports: [TableViewComponent, CodeButtonComponent, IrisReviewAssessmentButtonComponent, ManageAssessmentButtonsComponent] },
            add: {
                imports: [
                    MockTableViewComponent,
                    MockComponent(CodeButtonComponent),
                    MockComponent(IrisReviewAssessmentButtonComponent),
                    MockComponent(ManageAssessmentButtonsComponent),
                ],
            },
        });

        TestBed.compileComponents().then(() => {
            fixture = TestBed.createComponent(ExerciseScoresComponent);
            component = fixture.componentInstance;
            resultService = TestBed.inject(ResultService);
            participationService = TestBed.inject(ParticipationService);
            programmingSubmissionService = TestBed.inject(ProgrammingSubmissionService);
            courseService = TestBed.inject(CourseManagementService);
            exerciseService = TestBed.inject(ExerciseService);
            profileService = TestBed.inject(ProfileService);
            component.exercise.set(exercise);
            vi.spyOn(programmingSubmissionService, 'unsubscribeAllWebsocketTopics');
            component.paramSub = new Subscription();
        });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Initialization', () => {
        it('should be correctly set onInit', () => {
            const findCourseSpy = vi.spyOn(courseService, 'find');
            const findExerciseSpy = vi.spyOn(exerciseService, 'find');

            component.ngOnInit();

            expect(findCourseSpy).toHaveBeenCalledExactlyOnceWith(1);
            expect(findExerciseSpy).toHaveBeenCalledExactlyOnceWith(2);
        });
    });

    describe('Pagination / lazy loading', () => {
        it('should load page on lazy load event', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipationScores').mockReturnValue(
                of({
                    content: dtosToFilter,
                    totalElements: dtosToFilter.length,
                }),
            );

            component.exercise.set(exercise);
            component.onLazyLoad({ first: 0, rows: 50 });

            expect(searchSpy).toHaveBeenCalledOnce();
            expect(component.participations()).toEqual(dtosToFilter);
            expect(component.totalRows()).toBe(dtosToFilter.length);
            expect(component.isLoading()).toBe(false);
        });

        it('should update result filter and reload', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipationScores').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onLazyLoad({ first: 0, rows: 50 });
            searchSpy.mockClear();

            component.updateParticipationFilter(component.FilterProp.MANUAL);

            expect(component.activeFilter()).toBe(component.FilterProp.MANUAL);
            expect(searchSpy).toHaveBeenCalledOnce();
        });

        it('should refresh properly', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipationScores').mockReturnValue(of({ content: [sampleDto], totalElements: 1 }));

            component.onLazyLoad({ first: 0, rows: 50 });
            searchSpy.mockClear();

            component.refresh();

            expect(searchSpy).toHaveBeenCalledOnce();
            expect(component.participations()).toEqual([sampleDto]);
            expect(component.isLoading()).toBe(false);
        });

        it('should reset filter options and reload', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipationScores').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onLazyLoad({ first: 0, rows: 50 });
            component.rangeFilter.set(new Range(0, 10));
            component.activeFilter.set(FilterProp.SUCCESSFUL);
            searchSpy.mockClear();

            component.resetFilterOptions();

            expect(component.rangeFilter()).toBeUndefined();
            expect(component.activeFilter()).toBe(FilterProp.ALL);
            expect(searchSpy).toHaveBeenCalledOnce();
        });
    });

    describe('Navigation links', () => {
        it('should get exercise participation link for exercise without an exercise group', () => {
            const expectedLink = ['/course-management', course.id!.toString(), 'programming-exercises', exercise.id!.toString(), 'participations', '1', 'submissions'];
            component.course.set(course);

            const returnedLink = component.getExerciseParticipationsLink(1);

            expect(returnedLink).toEqual(expectedLink);
        });

        it('should get exercise participation link for exercise with an exercise group', () => {
            const expectedLink = [
                '/course-management',
                course.id!.toString(),
                'exams',
                '1',
                'exercise-groups',
                '1',
                'programming-exercises',
                exercise.id!.toString(),
                'participations',
                '2',
            ];
            component.course.set(course);
            component.exercise.set({
                ...exercise,
                exerciseGroup: {
                    id: 1,
                    exam: {
                        id: 1,
                    },
                },
            });

            const returnedLink = component.getExerciseParticipationsLink(2);

            expect(returnedLink).toEqual(expectedLink);
        });
    });

    describe('Relevant filters', () => {
        it.each([
            [FilterProp.ALL, { type: ExerciseType.PROGRAMMING } as Exercise, false, true],
            [FilterProp.ALL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.SUCCESSFUL, { type: ExerciseType.PROGRAMMING }, false, true],
            [FilterProp.SUCCESSFUL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.UNSUCCESSFUL, { type: ExerciseType.PROGRAMMING }, false, true],
            [FilterProp.UNSUCCESSFUL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.BUILD_FAILED, { type: ExerciseType.PROGRAMMING }, false, true],
            [FilterProp.BUILD_FAILED, { type: ExerciseType.TEXT }, true, false],
            [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: true }, false, true],
            [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
            [FilterProp.MANUAL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.AUTOMATIC, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: true }, false, true],
            [FilterProp.AUTOMATIC, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
            [FilterProp.AUTOMATIC, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: true }, true, true],
            [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: false }, false, false],
            [FilterProp.LOCKED, { type: ExerciseType.TEXT }, true, false],
        ])(
            'should determine if filter is relevant for exercise configuration',
            (filter: FilterProp, ex: Partial<Exercise>, newManualResultsAllowed: boolean, expected: boolean) => {
                component.exercise.set(ex as Exercise);
                component.newManualResultAllowed.set(newManualResultsAllowed);
                expect(component.relevantFilters().includes(filter)).toBe(expected);
            },
        );
    });

    describe('getBuildPlanUrl', () => {
        it('should construct build plan URL from template', () => {
            component.exercise.set({
                type: ExerciseType.PROGRAMMING,
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                projectKey: 'key',
            } as ProgrammingExercise);

            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'https://example.com/job/{projectKey}/job/{buildPlanId}' } as ProfileInfo);

            expect(component.getBuildPlanUrl(sampleDto)).toBe('https://example.com/job/key/job/1');
        });

        it('should return undefined when no template available', () => {
            component.exercise.set({
                type: ExerciseType.PROGRAMMING,
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
                projectKey: 'key',
            } as ProgrammingExercise);

            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({} as ProfileInfo);

            expect(component.getBuildPlanUrl(sampleDto)).toBeUndefined();
        });
    });

    describe('toResult', () => {
        it('should return undefined when dto has no resultId', () => {
            const dto: ParticipationScoreDTO = { ...sampleDto, resultId: undefined };
            expect(component.toResult(dto)).toBeUndefined();
        });

        it('should build a Result from dto fields', () => {
            const dto: ParticipationScoreDTO = {
                ...sampleDto,
                resultId: 42,
                score: 75,
                successful: true,
                assessmentType: AssessmentType.AUTOMATIC,
            };

            const result = component.toResult(dto);

            expect(result).toEqual(
                expect.objectContaining({
                    id: 42,
                    score: 75,
                    successful: true,
                    assessmentType: AssessmentType.AUTOMATIC,
                }),
            );
        });
    });

    describe('toParticipation', () => {
        it('should build a Participation with submissions when submissionId and resultId are present', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING });
            const dto: ParticipationScoreDTO = {
                ...sampleDto,
                participationId: 10,
                submissionId: 20,
                resultId: 30,
                score: 90,
                successful: true,
                assessmentType: AssessmentType.MANUAL,
            };

            const participation = component.toParticipation(dto);

            expect(participation.id).toBe(10);
            expect(participation.type).toBe(ParticipationType.PROGRAMMING);
            expect(participation.submissions).toHaveLength(1);
            expect(participation.submissions![0].id).toBe(20);
            expect(participation.submissions![0].results).toHaveLength(1);
            expect(participation.submissions![0].results![0].id).toBe(30);
        });

        it('should build a Participation with empty submissions when no submissionId', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT });
            const dto: ParticipationScoreDTO = { ...sampleDto, submissionId: undefined, resultId: undefined };

            const participation = component.toParticipation(dto);

            expect(participation.type).toBe(ParticipationType.STUDENT);
            expect(participation.submissions).toHaveLength(0);
        });

        it('should produce a plain submission without programming fields for a non-programming exercise', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT });
            const dto: ParticipationScoreDTO = { ...sampleDto, participationId: 10, submissionId: 20, resultId: 30, buildFailed: true };

            const participation = component.toParticipation(dto);

            expect(participation.type).toBe(ParticipationType.STUDENT);
            expect(participation.submissions).toHaveLength(1);
            expect(participation.submissions![0].id).toBe(20);
            expect(participation.submissions![0].results).toHaveLength(1);
            // A text exercise must not pick up the programming-only fields, even when the DTO carries buildFailed.
            expect(participation.submissions![0].submissionExerciseType).toBeUndefined();
            expect('buildFailed' in participation.submissions![0]).toBe(false);
        });

        it('should produce a programming submission with buildFailed: true when dto.buildFailed is true', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING });
            const dto: ParticipationScoreDTO = {
                ...sampleDto,
                participationId: 10,
                submissionId: 20,
                buildFailed: true,
            };

            const participation = component.toParticipation(dto);

            expect(participation.type).toBe(ParticipationType.PROGRAMMING);
            expect(participation.submissions).toHaveLength(1);
            expect((participation.submissions![0] as any).submissionExerciseType).toBe('programming');
            expect((participation.submissions![0] as any).buildFailed).toBe(true);
        });
    });

    describe('toProgrammingParticipation', () => {
        it('should keep the programming exercise and iris assessment from the score dto', () => {
            const irisAssessment = { id: 19, verdict: IrisVerdict.SUSPICIOUS };
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING } as ProgrammingExercise);
            const dto: ParticipationScoreDTO = {
                ...sampleDto,
                participationId: 10,
                submissionCount: 3,
                irisAssessment,
            };

            const participation = component.toProgrammingParticipation(dto);

            expect(participation.id).toBe(10);
            expect(participation.type).toBe(ParticipationType.PROGRAMMING);
            expect(participation.exercise).toBe(component.toProgrammingExercise());
            expect(participation.submissionCount).toBe(3);
            expect(participation.irisAssessment).toBe(irisAssessment);
        });
    });

    describe('Export names', () => {
        it('should export names correctly for individual students', () => {
            const exportDto: ParticipationNameExportDTO = { participantName: 'participantName', participantIdentifier: 'login1' };
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([exportDto]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            expect(resultServiceStub).toHaveBeenCalledExactlyOnceWith(['participantName'], 'results-names.csv');
        });

        it('should export names with team students format', () => {
            const exportDto: ParticipationNameExportDTO = {
                participantName: 'Team A',
                participantIdentifier: 'team-a',
                teamStudentNames: ['Alice', 'Bob'],
            };
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([exportDto]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            expect(resultServiceStub).toHaveBeenCalledOnce();
            const rows: string[] = resultServiceStub.mock.calls[0][0];
            expect(rows[0]).toBe('Team Name,Team Short Name,Students');
            expect(rows[1]).toContain('Team A');
            expect(rows[1]).toContain('Alice');
        });

        it('should not export when participant list is empty', () => {
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            expect(resultServiceStub).not.toHaveBeenCalled();
        });
    });

    describe('Row actions visibility', () => {
        function getRowActionsTemplateRef(): TemplateRef<{ $implicit: ParticipationScoreDTO }> | null {
            const tableView = fixture.debugElement.query(By.directive(MockTableViewComponent));
            return ngMocks.input(tableView, 'rowActions');
        }

        function renderRowActions(dto: ParticipationScoreDTO): HTMLElement {
            const templateRef = getRowActionsTemplateRef();
            expect(templateRef).not.toBeNull();
            const view = templateRef!.createEmbeddedView({ $implicit: dto });
            view.detectChanges();
            return view.rootNodes[0] as HTMLElement;
        }

        beforeEach(() => {
            // The first detectChanges() call runs ngOnInit(), which loads course/exercise via the
            // mocked services and would otherwise clobber any exercise()/course() set beforehand.
            // Run it once here so every test below can safely override those signals afterwards.
            fixture.detectChanges();
            component.course.set(course);
        });

        it('should not render a row actions column for users below tutor level', () => {
            component.exercise.set({ ...exercise, isAtLeastTutor: false, isAtLeastInstructor: false });
            fixture.detectChanges();

            expect(getRowActionsTemplateRef()).toBeNull();
        });

        it('should show only the Iris assessment review button for tutors', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastTutor: true, isAtLeastInstructor: false });
            fixture.detectChanges();

            const root = renderRowActions(sampleDto);

            expect(root.querySelector('jhi-iris-review-assessment-button')).not.toBeNull();
            expect(root.querySelector('jhi-code-button')).toBeNull();
            expect(root.querySelector('jhi-manage-assessment-buttons')).toBeNull();
        });

        it('should show all row actions for instructors', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastTutor: true, isAtLeastInstructor: true });
            fixture.detectChanges();

            const root = renderRowActions(sampleDto);

            expect(root.querySelector('jhi-iris-review-assessment-button')).not.toBeNull();
            expect(root.querySelector('jhi-code-button')).not.toBeNull();
            expect(root.querySelector('jhi-manage-assessment-buttons')).not.toBeNull();
        });

        it('should hide the Iris assessment review button for test run (practice mode) participations', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastTutor: true, isAtLeastInstructor: true });
            fixture.detectChanges();

            const root = renderRowActions({ ...sampleDto, testRun: true });

            expect(root.querySelector('jhi-iris-review-assessment-button')).toBeNull();
            expect(root.querySelector('jhi-manage-assessment-buttons')).not.toBeNull();
        });

        it('should not show the Iris assessment review button for non-programming exercises even for instructors', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT, isAtLeastTutor: true, isAtLeastInstructor: true });
            fixture.detectChanges();

            const root = renderRowActions(sampleDto);

            expect(root.querySelector('jhi-iris-review-assessment-button')).toBeNull();
            expect(root.querySelector('jhi-manage-assessment-buttons')).not.toBeNull();
        });
    });
});

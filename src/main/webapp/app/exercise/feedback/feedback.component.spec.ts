import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FEEDBACK_SUGGESTION_IDENTIFIER, Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { ProgrammingFeedbackItemService } from 'app/exercise/feedback/item/programming-feedback-item.service';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Subject, of, throwError } from 'rxjs';
import { FeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';

describe('FeedbackComponent', () => {
    let comp: FeedbackComponent;
    let fixture: ComponentFixture<FeedbackComponent>;

    let exercise: ProgrammingExercise;
    let buildLogService: BuildLogService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let feedbackItemService: ProgrammingFeedbackItemService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

    let buildlogsStub: ReturnType<typeof vi.spyOn>;
    let getFeedbackDetailsForResultStub: ReturnType<typeof vi.spyOn>;

    const feedbackReference = {
        id: 1,
        result: { id: 2 } as Result,
        hasLongFeedback: false,
    } as Feedback;

    const makeFeedback = (fb: Feedback) => {
        return Object.assign({ type: FeedbackType.AUTOMATIC, text: '', detailText: '', credits: 0 } as Feedback, fb);
    };

    const makeFeedbackItem = (item: FeedbackItem) => {
        return Object.assign(
            {
                type: 'Reviewer',
                credits: 0,
                title: undefined,
                positive: undefined,
            } as FeedbackItem,
            item,
        );
    };

    const generateSCAFeedbackPair = (
        showDetails: boolean,
        category: string,
        credits: number,
        penalty: number,
        { line = 1, column = undefined }: { line?: number; column?: number } = {},
    ) => {
        return {
            fb: makeFeedback({
                text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + category,
                detailText: JSON.stringify({
                    filePath: 'www/packet/File.java',
                    startLine: line,
                    startColumn: column,
                    rule: 'Rule',
                    message: 'This is a code issue',
                    penalty,
                }),
                credits,
                positive: false,
            }),
            item: makeFeedbackItem({
                type: 'Static Code Analysis',
                name: 'artemisApp.result.detail.codeIssue.name',
                title: 'artemisApp.result.detail.codeIssue.title',
                text: showDetails ? 'Rule: This is a code issue' : 'This is a code issue',
                credits,
                positive: false,
                feedbackReference,
            }),
        };
    };

    const generateTestCaseFeedbackPair = (showDetails: boolean, name: string, message: string | undefined, credits: number) => {
        return {
            fb: makeFeedback({
                testCase: { testName: name },
                detailText: message,
                credits,
                positive: credits > 0,
            }),
            item: makeFeedbackItem({
                type: 'Test',
                name: showDetails ? 'artemisApp.result.detail.test.name' : 'artemisApp.result.detail.feedback',
                text: message,
                credits,
                positive: credits > 0,
                title: showDetails ? (credits > 0 ? 'artemisApp.result.detail.test.passed' : 'artemisApp.result.detail.test.failed') : undefined,
                feedbackReference,
            }),
        };
    };

    const generateManualFeedbackPair = (showDetails: boolean, title: string, text: string, credits = 0) => {
        return {
            fb: makeFeedback({
                type: FeedbackType.MANUAL,
                text: title,
                detailText: text,
                credits,
                positive: credits > 0,
            }),
            item: makeFeedbackItem({
                type: 'Reviewer',
                name: showDetails ? 'artemisApp.course.tutor' : 'artemisApp.result.detail.feedback',
                title,
                text,
                credits,
                positive: credits > 0,
                feedbackReference,
            }),
        };
    };

    const generateFeedbacksAndExpectedItems = (showTestDetails = false) => {
        const feedbacks: Feedback[] = [];
        const expectedItems: FeedbackNode[] = [];
        const addPair = (pair: { fb: Feedback; item: FeedbackItem }) => {
            feedbacks.push(pair.fb);
            expectedItems.push(pair.item);
        };
        addPair(generateSCAFeedbackPair(showTestDetails, 'Bad Practice', -2, 2));
        addPair(generateSCAFeedbackPair(showTestDetails, 'Styling', -0.5, 1, { column: 10 }));
        addPair(generateSCAFeedbackPair(showTestDetails, 'Styling', -0.5, 1, { line: 2, column: 1 }));
        addPair(generateManualFeedbackPair(showTestDetails, 'Positive', 'This is good', 4));
        addPair(generateManualFeedbackPair(showTestDetails, 'Negative', 'This is bad', -2));
        addPair(generateManualFeedbackPair(showTestDetails, 'Neutral', 'This is neutral', 0));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase1', 'This failed.', 0));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase2', 'This passed.', 3));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase3', undefined, 3));

        if (!showTestDetails) {
            expectedItems.pop();
            expectedItems.unshift(
                makeFeedbackItem({
                    type: 'Test',
                    name: 'artemisApp.result.detail.feedback',
                    title: 'artemisApp.result.detail.test.passedTest',
                    positive: true,
                    credits: 3,
                    feedbackReference,
                }),
            );
        }

        return { feedbacks, expectedItems };
    };

    const generateProgrammingSubmission = (buildFailed: boolean) => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.buildFailed = buildFailed;
        return programmingSubmission;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },

                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackComponent);
        comp = fixture.componentInstance;
        exercise = {
            id: 42,
            maxPoints: 100,
            bonusPoints: 0,
            type: ExerciseType.PROGRAMMING,
            staticCodeAnalysisEnabled: true,
            maxStaticCodeAnalysisPenalty: 20,
            projectKey: 'somekey',
        } as ProgrammingExercise;
        const course = new Course();
        course.id = 3;
        course.title = 'Testcourse';
        exercise.course = course;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.componentRef.setInput('participation', {
            id: 55,
            type: ParticipationType.PROGRAMMING,
            participantIdentifier: 'student42',
            repositoryUri: 'https://artemis.tum.de/projects/somekey/repos/somekey-student42',
        } as ProgrammingExerciseStudentParticipation);
        fixture.componentRef.setInput('result', {
            id: 89,
            submission: {
                participation: {
                    id: 55,
                    type: ParticipationType.PROGRAMMING,
                    participantIdentifier: 'student42',
                    repositoryUri: 'https://artemis.tum.de/projects/somekey/repos/somekey-student42',
                },
                buildFailed: true,
                commitHash: 'assessed-commit-hash',
            },
        } as Result);
        buildLogService = TestBed.inject(BuildLogService);
        resultService = TestBed.inject(ResultService);
        profileService = TestBed.inject(ProfileService);
        feedbackItemService = TestBed.inject(ProgrammingFeedbackItemService);
        programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
        buildlogsStub = vi.spyOn(buildLogService, 'getBuildLogs').mockReturnValue(of([]));
        getFeedbackDetailsForResultStub = vi.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));
        // Set profile info
        const profileInfo = new ProfileInfo();
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set the exercise from the participation if available', () => {
        fixture.componentRef.setInput('exercise', undefined);
        // Provide the exercise via the participation input (new object reference so the computed re-evaluates).
        fixture.componentRef.setInput('participation', { ...comp.participation(), exercise });

        comp.ngOnInit();

        expect(comp.resolvedExercise()).toEqual(exercise);
        expect(comp.course()).toEqual(exercise.course);
    });

    it('should set the exercise type from the exercise if not available otherwise', () => {
        exercise.type = ExerciseType.MODELING;
        fixture.componentRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(comp.exerciseType()).toBe(ExerciseType.MODELING);
    });

    it('should set the exercise type from a programming participation if not available otherwise', () => {
        fixture.componentRef.setInput('exercise', undefined);
        // exerciseType() reads the participation input directly, so set a programming participation (without an
        // exercise) to exercise the fallback branch.
        fixture.componentRef.setInput('participation', { id: 55, type: ParticipationType.PROGRAMMING } as ProgrammingExerciseStudentParticipation);

        comp.ngOnInit();

        expect(comp.exerciseType()).toBe(ExerciseType.PROGRAMMING);
    });

    it('should generate commit link for programming exercise result with submission, participation and exercise', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.result().feedbacks = feedbacks;
        comp.result().submission = {
            ...comp.result().submission,
            type: SubmissionType.MANUAL,
            commitHash: '123456789ab',
        } as ProgrammingSubmission;

        comp.ngOnInit();

        expect(comp.getCommitHash()).toBe('123456789ab');
    });

    it('should not try to retrieve the feedbacks from the server if provided result has feedbacks', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.result().feedbacks = feedbacks;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(false);
    });

    it('should build the score chart when showScoreChart is set and feedback items exist', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        fixture.componentRef.setInput('showScoreChart', true);
        comp.result().feedbacks = feedbacks;

        comp.ngOnInit();

        // updateChart ran (the chart stays visible) and the derived chart signals are available.
        expect(comp.scoreChartVisible()).toBe(true);
        expect(comp.feedbackItemNodes()?.length).toBeGreaterThan(0);
        expect(comp.scoreChartData()).toBeDefined();
        expect(comp.scoreChartConfig()).toBeDefined();
    });

    it('should load historical source code from the assessed commit without blocking feedback', () => {
        const repositoryFiles = new Subject<Map<string, string>>();
        const getFilesSpy = vi.spyOn(programmingExerciseParticipationService, 'getSelectedParticipationRepositoryFilesAtCommit').mockReturnValue(repositoryFiles);
        comp.result().feedbacks = [
            makeFeedback({
                text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Check this implementation`,
                detailText: 'The returned value is wrong.',
                reference: 'file:src/main/java/Example.java_line:3',
            }),
        ];

        comp.ngOnInit();

        expect(getFilesSpy).toHaveBeenCalledOnce();
        expect(getFilesSpy).toHaveBeenCalledWith(42, 55, 'assessed-commit-hash', ['src/main/java/Example.java']);
        expect(comp.feedbackItemNodes()).toBeDefined();
        expect(comp.isLoading()).toBe(false);

        repositoryFiles.next(new Map([['src/main/java/Example.java', ['1', '2', 'historical answer', '4', '5'].join('\n')]]));

        const feedbackItem = (comp.feedbackItemNodes()?.[0] as FeedbackGroup).members[0];
        expect(feedbackItem.codeReference).toEqual({
            filePath: 'src/main/java/Example.java',
            line: 3,
            lines: [
                { line: 1, code: '1', referenced: false },
                { line: 2, code: '2', referenced: false },
                { line: 3, code: 'historical answer', referenced: true },
                { line: 4, code: '4', referenced: false },
                { line: 5, code: '5', referenced: false },
            ],
        });
    });

    it('should request each referenced file only once', () => {
        const getFilesSpy = vi.spyOn(programmingExerciseParticipationService, 'getSelectedParticipationRepositoryFilesAtCommit').mockReturnValue(of(new Map()));
        comp.result().feedbacks = [
            makeFeedback({ text: `${FEEDBACK_SUGGESTION_IDENTIFIER}First issue`, reference: 'file:src/main/java/Example.java_line:2' }),
            makeFeedback({ text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Second issue`, reference: 'file:src/main/java/Example.java_line:4' }),
            makeFeedback({ text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Third issue`, reference: 'file:src/main/java/Other.java_line:1' }),
        ];

        comp.ngOnInit();

        expect(getFilesSpy).toHaveBeenCalledOnce();
        expect(getFilesSpy).toHaveBeenCalledWith(42, 55, 'assessed-commit-hash', ['src/main/java/Example.java', 'src/main/java/Other.java']);
    });

    it('should load surrounding source code for ranged programming AI feedback', () => {
        vi.spyOn(programmingExerciseParticipationService, 'getSelectedParticipationRepositoryFilesAtCommit').mockReturnValue(
            of(new Map([['src/main/java/Example.java', ['1', '2', '3', '4', '5', '6', '7'].join('\n')]])),
        );
        comp.result().feedbacks = [
            makeFeedback({
                text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Check this implementation`,
                detailText: 'The returned value is wrong.',
                reference: 'file:src/main/java/Example.java_line:3-5',
            }),
        ];

        comp.ngOnInit();

        const feedbackItem = (comp.feedbackItemNodes()?.[0] as FeedbackGroup).members[0];
        expect(feedbackItem.codeReference?.lines).toEqual([
            { line: 1, code: '1', referenced: false },
            { line: 2, code: '2', referenced: false },
            { line: 3, code: '3', referenced: true },
            { line: 4, code: '4', referenced: true },
            { line: 5, code: '5', referenced: true },
            { line: 6, code: '6', referenced: false },
            { line: 7, code: '7', referenced: false },
        ]);
    });

    it('should truncate oversized programming AI feedback line ranges', () => {
        const fileContent = Array.from({ length: 100 }, (_, index) => String(index + 1)).join('\n');
        vi.spyOn(programmingExerciseParticipationService, 'getSelectedParticipationRepositoryFilesAtCommit').mockReturnValue(
            of(new Map([['src/main/java/Example.java', fileContent]])),
        );
        comp.result().feedbacks = [
            makeFeedback({
                text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Check this implementation`,
                detailText: 'The returned value is wrong.',
                reference: 'file:src/main/java/Example.java_line:3-1000000',
            }),
        ];

        comp.ngOnInit();

        const feedbackItem = (comp.feedbackItemNodes()?.[0] as FeedbackGroup).members[0];
        expect(feedbackItem.codeReference?.lines).toHaveLength(50);
        expect(feedbackItem.codeReference?.lines?.at(0)?.line).toBe(1);
        expect(feedbackItem.codeReference?.lines?.at(-1)?.line).toBe(50);
    });

    it('should still show feedback when loading the assessed repository fails', () => {
        vi.spyOn(programmingExerciseParticipationService, 'getSelectedParticipationRepositoryFilesAtCommit').mockReturnValue(throwError(() => new Error('repository unavailable')));
        comp.result().feedbacks = [
            makeFeedback({
                text: `${FEEDBACK_SUGGESTION_IDENTIFIER}Check this implementation`,
                detailText: 'The returned value is wrong.',
                reference: 'file:src/main/java/Example.java_line:2',
            }),
        ];

        comp.ngOnInit();

        const feedbackItem = (comp.feedbackItemNodes()?.[0] as FeedbackGroup).members[0];
        expect(feedbackItem.codeReference).toEqual({ filePath: 'src/main/java/Example.java', line: 2 });
        expect(comp.isLoading()).toBe(false);
        expect(comp.loadingFailed()).toBe(false);
    });

    it('should hide the score chart when there is no chart data', () => {
        fixture.componentRef.setInput('showScoreChart', true);
        // No feedbacks -> no feedback item nodes -> updateChart hides the chart.
        comp['updateChart']([]);

        expect(comp.scoreChartVisible()).toBe(false);
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have feedbacks', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: feedbacks } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledOnce();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(55, comp.result());
        expect(comp.isLoading()).toBe(false);
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and a submission was provided which was marked with build failed.', () => {
        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.buildLogs()).toHaveLength(0);
        expect(comp.isLoading()).toBe(false);
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.MODELING } as Exercise);
        comp.result().submission = new ModelingSubmission();

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.feedbackItemNodes()).toBeUndefined();
        expect(comp.isLoading()).toBe(false);
    });

    it('should not try to retrieve build logs if submission was not marked with build failed', () => {
        comp.result().submission = generateProgrammingSubmission(false);

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.buildLogs()).toBeUndefined();
        expect(comp.isLoading()).toBe(false);
    });

    it('fetchBuildLogs should suppress 403 error', () => {
        const response = new HttpErrorResponse({ status: 403 });
        buildlogsStub.mockReturnValue(throwError(() => response));

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.loadingFailed()).toBe(false);
        expect(comp.isLoading()).toBe(false);
    });

    it('fetchBuildLogs should not suppress errors with status other than 403', () => {
        const response = new HttpErrorResponse({ status: 500 });
        buildlogsStub.mockReturnValue(throwError(() => response));
        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.loadingFailed()).toBe(true);
        expect(comp.isLoading()).toBe(false);
    });

    it('should not show test details to students', () => {
        const createSpy = vi.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.result().feedbacks = feedbacks;

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, false);
    });

    it('should show test details to tutors', () => {
        const createSpy = vi.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.result().feedbacks = feedbacks;

        exercise.isAtLeastTutor = true;
        fixture.componentRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, true);
    });

    it('should show test details to students for programming exercises with show test names on', () => {
        const createSpy = vi.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.result().feedbacks = feedbacks;

        exercise.showTestNamesToStudents = true;
        fixture.componentRef.setInput('exercise', exercise);

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, true);
    });

    describe('when opened via DialogService (inputValues forwarded via setInput)', () => {
        it('reads the inputs forwarded by the dialog before initializing feedback', () => {
            // The standalone-feedback page binds inputs via the template; PrimeNG's DialogService forwards `inputValues`
            // by calling componentRef.setInput on the same signal inputs. This verifies that dialog-forwarded path.
            TestBed.resetTestingModule();

            const dialogExercise = { id: 7, type: ExerciseType.PROGRAMMING, maxPoints: 100, bonusPoints: 0, course: exercise.course } as ProgrammingExercise;
            const dialogParticipation = { id: 99, type: ParticipationType.PROGRAMMING } as ProgrammingExerciseStudentParticipation;
            const dialogResult = { id: 123, submission: { participation: dialogParticipation } } as Result;

            TestBed.configureTestingModule({
                providers: [
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: ProfileService, useClass: MockProfileService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            });

            const dialogFixture = TestBed.createComponent(FeedbackComponent);
            const dialogComp = dialogFixture.componentInstance;
            vi.spyOn(TestBed.inject(BuildLogService), 'getBuildLogs').mockReturnValue(of([]));
            vi.spyOn(TestBed.inject(ResultService), 'getFeedbackDetailsForResult').mockReturnValue(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));
            vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue(new ProfileInfo());

            dialogFixture.componentRef.setInput('exercise', dialogExercise);
            dialogFixture.componentRef.setInput('result', dialogResult);
            dialogFixture.componentRef.setInput('participation', dialogParticipation);
            dialogFixture.componentRef.setInput('showScoreChart', true);
            dialogFixture.componentRef.setInput('taskName', 'Task 1');
            dialogFixture.componentRef.setInput('numberOfNotExecutedTests', 4);

            dialogComp.ngOnInit();

            expect(dialogComp.exercise()).toBe(dialogExercise);
            expect(dialogComp.result()).toBe(dialogResult);
            expect(dialogComp.participation()).toBe(dialogParticipation);
            expect(dialogComp.exerciseType()).toBe(ExerciseType.PROGRAMMING);
            expect(dialogComp.showScoreChart()).toBe(true);
            expect(dialogComp.taskName()).toBe('Task 1');
            expect(dialogComp.numberOfNotExecutedTests()).toBe(4);
        });
    });
});

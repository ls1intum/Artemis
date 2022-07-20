import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { ArtemisTestModule } from '../../test.module';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FeedbackItem, FeedbackItemType, ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { FeedbackCollapseComponent } from 'app/exercises/shared/result/feedback-collapse.component';
import { NgbActiveModal, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { BarChartModule } from '@swimlane/ngx-charts';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';
import { Course } from 'app/entities/course.model';

describe('ResultDetailComponent', () => {
    let comp: ResultDetailComponent;
    let fixture: ComponentFixture<ResultDetailComponent>;
    let debugElement: DebugElement;

    let exercise: ProgrammingExercise;
    let buildLogService: BuildLogService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let buildlogsStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;

    // Template for Bitbucket commit hash url
    const commitHashURLTemplate = 'https://bitbucket.ase.in.tum.de/projects/{projectKey}/repos/{repoSlug}/commits/{commitHash}';

    const makeFeedback = (fb: Feedback) => {
        return Object.assign({ type: FeedbackType.AUTOMATIC, text: '', detailText: '', credits: 0 } as Feedback, fb);
    };

    const makeFeedbackItem = (item: FeedbackItem) => {
        return Object.assign({ type: FeedbackItemType.Feedback, credits: 0, title: undefined, positive: undefined } as FeedbackItem, item);
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
                type: FeedbackItemType.Issue,
                category: 'Code Issue',
                title: category + ' Issue in file www/packet/File.java at line ' + line + (column != undefined ? ' column ' + column : ''),
                text: showDetails ? 'Rule: This is a code issue' : 'This is a code issue',
                credits: -penalty,
                actualCredits: credits,
                positive: false,
            }),
        };
    };

    const generateTestCaseFeedbackPair = (showDetails: boolean, name: string, message: string | undefined, credits: number) => {
        return {
            fb: makeFeedback({
                text: name,
                detailText: message,
                credits,
                positive: credits > 0,
            }),
            item: makeFeedbackItem({
                type: FeedbackItemType.Test,
                category: showDetails ? 'Test Case' : 'Feedback',
                text: message,
                credits,
                positive: credits > 0,
                title: showDetails ? `Test ${name} ${credits > 0 ? 'passed' : 'failed'}` : undefined,
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
                type: FeedbackItemType.Feedback,
                category: showDetails ? 'Tutor' : 'Feedback',
                title,
                text,
                credits,
                positive: credits > 0,
            }),
        };
    };

    const generateFeedbacksAndExpectedItems = (showTestDetails = false) => {
        const feedbacks: Feedback[] = [];
        const expectedItems: FeedbackItem[] = [];
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
            expectedItems.unshift(makeFeedbackItem({ type: FeedbackItemType.Test, category: 'Feedback', title: '1 passed test', positive: true, credits: 3 }));
        }

        return { feedbacks, expectedItems };
    };

    const generateProgrammingSubmission = (buildFailed: boolean) => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.buildFailed = buildFailed;
        return programmingSubmission;
    };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(BarChartModule)],
            declarations: [ResultDetailComponent, TranslatePipeMock, MockPipe(ArtemisDatePipe), MockComponent(FeedbackCollapseComponent), MockDirective(NgbTooltip)],
            providers: [MockProvider(NgbActiveModal), MockProvider(ResultService), MockProvider(BuildLogService), MockProvider(ProfileService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultDetailComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;

                exercise = {
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

                comp.exercise = exercise;

                comp.result = {
                    id: 89,
                    participation: {
                        id: 55,
                        type: ParticipationType.PROGRAMMING,
                        participantIdentifier: 'student42',
                        repositoryUrl: 'https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42',
                    },
                } as Result;

                buildLogService = debugElement.injector.get(BuildLogService);
                resultService = debugElement.injector.get(ResultService);
                profileService = debugElement.injector.get(ProfileService);

                buildlogsStub = jest.spyOn(buildLogService, 'getBuildLogs').mockReturnValue(of([]));
                getFeedbackDetailsForResultStub = jest
                    .spyOn(resultService, 'getFeedbackDetailsForResult')
                    .mockReturnValue(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));

                // Set profile info
                const profileInfo = new ProfileInfo();
                profileInfo.commitHashURLTemplate = commitHashURLTemplate;
                jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(new BehaviorSubject(profileInfo));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set the exercise from the participation if available', () => {
        comp.exercise = undefined;
        comp.result.participation!.exercise = exercise;

        comp.ngOnInit();

        expect(comp.exercise).toEqual(exercise);
        expect(comp.course).toEqual(exercise.course);
    });

    it('should set the exercise type from the exercise if not available otherwise', () => {
        comp.exerciseType = undefined as any;
        exercise.type = ExerciseType.MODELING;
        comp.exercise = exercise;

        comp.ngOnInit();

        expect(comp.exerciseType).toBe(ExerciseType.MODELING);
    });

    it('should set the exercise type from a programming participation if not available otherwise', () => {
        comp.exerciseType = undefined as any;
        comp.exercise = undefined;
        comp.result.participation!.type = ParticipationType.PROGRAMMING;

        comp.ngOnInit();

        expect(comp.exerciseType).toBe(ExerciseType.PROGRAMMING);
    });

    it('should generate commit link for programming exercise result with submission, participation and exercise', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.result.submission = {
            type: SubmissionType.MANUAL,
            commitHash: '123456789ab',
        } as ProgrammingSubmission;

        comp.ngOnInit();

        expect(comp.getCommitHash()).toBe('123456789ab');
        expect(comp.getCommitUrl()).toBe('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789ab');
    });

    it('should not try to retrieve the feedbacks from the server if provided result has feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.filteredFeedbackList).toEqual(expectedItems);
        expect(comp.isLoading).toBeFalse();
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: feedbacks } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledOnce();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(comp.result.participation!.id!, comp.result.id);
        expect(comp.filteredFeedbackList).toIncludeSameMembers(expectedItems);
        expect(comp.isLoading).toBeFalse();
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and no submission was provided.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(comp.result.participation!.id, comp.result.id);
        expect(comp.buildLogs).toBeArrayOfSize(0);
        expect(comp.isLoading).toBeFalse();
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and a submission was provided which was marked with build failed.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(true);

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(comp.result.participation!.id, comp.result.id);
        expect(comp.buildLogs).toBeArrayOfSize(0);
        expect(comp.isLoading).toBeFalse();
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING', () => {
        comp.exerciseType = ExerciseType.MODELING;
        comp.result.submission = new ModelingSubmission();

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.feedbackList).toBe(undefined);
        expect(comp.isLoading).toBeFalse();
    });

    it('should not try to retrieve build logs if submission was not marked with build failed', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(false);

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.buildLogs).toBe(undefined);
        expect(comp.isLoading).toBeFalse();
    });

    it('fetchBuildLogs should suppress 403 error', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 403 });
        buildlogsStub.mockReturnValue(throwError(() => response));

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(comp.result.participation!.id, comp.result.id);
        expect(comp.loadingFailed).toBeFalse();
        expect(comp.isLoading).toBeFalse();
    });

    it('fetchBuildLogs should not suppress errors with status other than 403', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 500 });
        buildlogsStub.mockReturnValue(throwError(() => response));

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(comp.result.participation!.id, comp.result.id);
        expect(comp.loadingFailed).toBeTrue();
        expect(comp.isLoading).toBeFalse();
    });

    it('should show test names if showTestDetails is set to true', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems(true);
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.showTestDetails = true;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.filteredFeedbackList).toEqual(expectedItems);
        expect(comp.isLoading).toBeFalse();
    });

    it('should show a replacement title if automatic feedback is neither positive nor negative', () => {
        const feedback: Feedback = {
            type: FeedbackType.AUTOMATIC,
            text: 'automaticTestCase1',
            positive: undefined,
            credits: 0.3,
        };

        const expectedFeedbackItem: FeedbackItem = {
            category: 'Test Case',
            credits: 0.3,
            title: 'No result information for automaticTestCase1',
            type: FeedbackItemType.Test,
            text: undefined,
            previewText: undefined,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);
    });

    it('should show both the grading instruction feedback and the tutor feedback', () => {
        const gradingInstruction = new GradingInstruction();
        gradingInstruction.feedback = 'Grading Instruction Feedback';

        const feedback: Feedback = {
            type: FeedbackType.MANUAL,
            gradingInstruction,
            text: 'Feedback Title',
            detailText: 'Manual tutor feedback',
            credits: 0,
        };

        const expectedFeedbackItem: FeedbackItem = {
            type: FeedbackItemType.Feedback,
            category: 'Tutor',
            title: feedback.text,
            text: 'Grading Instruction Feedback\nManual tutor feedback',
            credits: 0,
            positive: undefined,
            previewText: undefined,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);

        feedback.type = FeedbackType.MANUAL_UNREFERENCED;
        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);

        // subsequent feedback is shown differently
        feedback.isSubsequent = true;
        expectedFeedbackItem.type = FeedbackItemType.Subsequent;
        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);

        // only grading instruction feedback should be shown if no detail text is available
        feedback.detailText = undefined;
        expectedFeedbackItem.text = 'Grading Instruction Feedback';
        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);
    });

    it('should hide grading instruction information if no details are shown', () => {
        const gradingInstruction = new GradingInstruction();
        gradingInstruction.feedback = 'Grading Instruction Feedback';

        const feedback: Feedback = {
            type: FeedbackType.MANUAL,
            gradingInstruction,
            text: 'Feedback Title',
            detailText: 'Manual tutor feedback',
            credits: 0,
        };

        const expectedFeedbackItem: FeedbackItem = {
            type: FeedbackItemType.Feedback,
            category: 'Feedback',
            title: feedback.text,
            text: 'Grading Instruction Feedback\nManual tutor feedback',
            credits: 0,
            positive: undefined,
            previewText: undefined,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem, ExerciseType.PROGRAMMING, false);
    });

    it('should show feedback generated from submission policies', () => {
        const feedback: Feedback = {
            type: FeedbackType.AUTOMATIC,
            text: `${SUBMISSION_POLICY_FEEDBACK_IDENTIFIER}Submission Penalty Policy`,
            detailText: 'You have submitted 2 more times than the submission limit of 10. This results in a deduction of 0.1 points!',
            positive: false,
            credits: -0.1,
        };

        const expectedFeedbackItem: FeedbackItem = {
            type: FeedbackItemType.Policy,
            category: 'Submission Policy',
            title: 'Submission Penalty Policy',
            text: feedback.detailText,
            previewText: undefined,
            positive: false,
            credits: feedback.credits,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem);
    });

    it('should only show the first line of feedback as preview', () => {
        const feedback: Feedback = {
            text: 'Summary',
            detailText: 'Multi\nLine\nText',
            credits: 0,
        };

        const expectedFeedbackItem: FeedbackItem = {
            type: FeedbackItemType.Feedback,
            category: 'Feedback',
            title: feedback.text,
            text: feedback.detailText,
            previewText: 'Multi',
            positive: undefined,
            credits: 0,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem, ExerciseType.QUIZ);
    });

    it('should shorten the preview text if it is long', () => {
        const feedback: Feedback = {
            text: 'Summary',
            detailText: '0'.repeat(400),
            credits: 0,
        };

        const expectedFeedbackItem: FeedbackItem = {
            type: FeedbackItemType.Feedback,
            category: 'Feedback',
            title: feedback.text,
            text: feedback.detailText,
            previewText: '0'.repeat(300),
            positive: undefined,
            credits: 0,
        };

        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem, ExerciseType.MODELING);

        // the first line should also be shortened if there is more feedback afterwards
        feedback.detailText = '0'.repeat(400) + '\nAdditional Line\n' + '1'.repeat(400);
        expectedFeedbackItem.text = feedback.detailText;
        shouldGenerateFeedbackItem(feedback, expectedFeedbackItem, ExerciseType.MODELING);
    });

    describe('static code analysis feedback formatting', () => {
        let baseScaIssue: StaticCodeAnalysisIssue;
        let baseExpectedFeedbackItem: FeedbackItem;

        beforeEach(() => {
            baseScaIssue = {
                filePath: 'src/Main.java',
                startLine: 1,
                endLine: 1,
                category: 'Formatting',
                message: 'SCA Message',
                priority: 'low',
                rule: 'Checkstyle',
            };
            baseExpectedFeedbackItem = {
                category: 'Code Issue',
                type: FeedbackItemType.Issue,
                actualCredits: 0,
                credits: 0,
                positive: false,
                previewText: undefined,
                text: 'Checkstyle: SCA Message',
            };
        });

        it('should show start and end lines', () => {
            baseScaIssue.endLine = 4;
            const feedback = createScaFeedback('SCA Rule', baseScaIssue);
            baseExpectedFeedbackItem.title = 'SCA Rule Issue in file src/Main.java at lines 1-4';

            shouldGenerateFeedbackItem(feedback, baseExpectedFeedbackItem);
        });

        it('should only show start line if end line is identical', () => {
            baseScaIssue.endLine = baseScaIssue.startLine;
            const feedback = createScaFeedback('SCA Rule', baseScaIssue);
            baseExpectedFeedbackItem.title = 'SCA Rule Issue in file src/Main.java at line 1';

            shouldGenerateFeedbackItem(feedback, baseExpectedFeedbackItem);
        });

        it('should show start and end columns if available', () => {
            baseScaIssue.endLine = 4;
            baseScaIssue.startColumn = 3;
            baseScaIssue.endColumn = 40;
            const feedback = createScaFeedback('SCA Rule', baseScaIssue);
            baseExpectedFeedbackItem.title = 'SCA Rule Issue in file src/Main.java at lines 1-4 columns 3-40';

            shouldGenerateFeedbackItem(feedback, baseExpectedFeedbackItem);
        });

        it('should only show start column if end column is identical', () => {
            baseScaIssue.endLine = baseScaIssue.startLine;
            baseScaIssue.startColumn = 45;
            baseScaIssue.endColumn = 45;
            const feedback = createScaFeedback('SCA Rule', baseScaIssue);
            baseExpectedFeedbackItem.title = 'SCA Rule Issue in file src/Main.java at line 1 column 45';

            shouldGenerateFeedbackItem(feedback, baseExpectedFeedbackItem);
        });

        const createScaFeedback = (text: string, scaIssue: StaticCodeAnalysisIssue): Feedback => {
            const feedback = new Feedback();
            feedback.type = FeedbackType.AUTOMATIC;
            feedback.text = `${STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER}${text}`;
            feedback.detailText = JSON.stringify(scaIssue);
            return feedback;
        };
    });

    const shouldGenerateFeedbackItem = (feedback: Feedback, expectedFeedbackItem: FeedbackItem, exerciseType: ExerciseType = ExerciseType.PROGRAMMING, showTestDetails = true) => {
        comp.exerciseType = exerciseType;
        comp.result.feedbacks = [feedback];
        comp.showTestDetails = showTestDetails;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.filteredFeedbackList).toEqual([expectedFeedbackItem]);
        expect(comp.isLoading).toBeFalse();
    };

    it('should filter the correct feedbacks when a filter is set', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.feedbackFilter = ['TestCase1', 'TestCase2', 'TestCase3'];

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.filteredFeedbackList).toEqual(expectedItems.filter((item) => item.type === FeedbackItemType.Test));
        expect(comp.isLoading).toBeFalse();
    });

    it('should generate correct class names for feedback items', () => {
        const { expectedItems } = generateFeedbacksAndExpectedItems();

        const expectedClasses = [
            'alert-success', // test case 1
            'alert-warning', // sca
            'alert-warning', // sca
            'alert-warning', // sca
            'alert-success', // manual 1
            'alert-danger', // manual 2
            'alert-warning', // manual 3
            'alert-danger', // test case 2
            'alert-success', // test case 3
        ];

        expectedItems.forEach((item, index) => expect(comp.getClassNameForFeedbackItem(item)).toEqual(expectedClasses[index]));
    });

    it('should calculate the correct chart values and update the score chart', () => {
        const { feedbacks, expectedItems } = setupComponent();

        expect(comp.filteredFeedbackList).toEqual(expectedItems);
        expect(comp.backupFilteredFeedbackList).toEqual(expectedItems);
        expect(comp.showScoreChartTooltip).toBeTrue();

        checkChartPreset(5, 5, '10', '5 of 6');
        expect(comp.isLoading).toBeFalse();

        // test score exceeding exercise maxpoints

        const feedbackPair1 = generateTestCaseFeedbackPair(true, '', '', 120);
        feedbacks.push(feedbackPair1.fb);
        expectedItems.push(feedbackPair1.item);

        comp.ngOnInit();

        expect(comp.filteredFeedbackList).toEqual(expectedItems);
        checkChartPreset(99, 1, '100 of 104', '1 of 6');

        // test negative > positive, limit at 0

        feedbacks.pop();
        expectedItems.pop();
        const feedbackPair2 = generateSCAFeedbackPair(true, 'Tohuwabohu', -200, 200);
        feedbacks.push(feedbackPair2.fb);
        expectedItems.push(feedbackPair2.item);

        comp.ngOnInit();

        expect(comp.filteredFeedbackList).toEqual(expectedItems);

        checkChartPreset(0, 10, '10', '10 of 206');
    });

    it('should filter feedback items correctly', () => {
        const { expectedItems } = setupComponent();
        const event = { isPositive: true, series: {} };
        let currentlyVisibleItems = expectedItems.filter((item) => !!item.positive);

        comp.onSelect(event);

        expect(comp.showOnlyPositiveFeedback).toBeTrue();
        expect(comp.showOnlyNegativeFeedback).toBeFalse();
        expect(comp.filteredFeedbackList).toEqual(currentlyVisibleItems);

        event.isPositive = false;
        currentlyVisibleItems = expectedItems.filter((item) => item.positive === false && item.actualCredits! < 0);

        comp.onSelect(event);

        expect(comp.showOnlyNegativeFeedback).toBeTrue();
        expect(comp.showOnlyPositiveFeedback).toBeFalse();
        expect(comp.filteredFeedbackList).toEqual(currentlyVisibleItems);

        comp.resetChartFilter();

        expect(comp.showOnlyNegativeFeedback).toBeFalse();
        expect(comp.filteredFeedbackList).toEqual(expectedItems);
    });

    const checkChartPreset = (d1: number, d2: number, l1: string, l2: string) => {
        expect(comp.ngxData[0].series).toHaveLength(2);
        expect(comp.ngxData[0].series[0].name).toBe('artemisApp.result.chart.points: ' + l1);
        expect(comp.ngxData[0].series[0].value).toBe(d1);
        expect(comp.ngxData[0].series[1].name).toBe('artemisApp.result.chart.deductions: ' + l2);
        expect(comp.ngxData[0].series[1].value).toBe(d2);
    };

    const setupComponent = () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems(true);
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.showScoreChart = true;
        comp.showTestDetails = true;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();
        return { feedbacks, expectedItems };
    };
});

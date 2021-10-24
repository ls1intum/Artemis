import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SinonStub, spy, stub } from 'sinon';
import { BehaviorSubject, of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { FeedbackItem, FeedbackItemType, ResultDetailComponent } from 'app/exercises/shared/result/result-detail.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { BuildLogService } from 'app/exercises/programming/shared/service/build-log.service';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { SubmissionType } from 'app/entities/submission.model';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ChartComponent } from 'app/shared/chart/chart.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FeedbackCollapseComponent } from 'app/exercises/shared/result/feedback-collapse.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

chai.use(sinonChai);
const expect = chai.expect;

describe('ResultDetailComponent', () => {
    let comp: ResultDetailComponent;
    let fixture: ComponentFixture<ResultDetailComponent>;
    let debugElement: DebugElement;

    let exercise: ProgrammingExercise;
    let buildLogService: BuildLogService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let buildlogsStub: SinonStub;
    let getFeedbackDetailsForResultStub: SinonStub;

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
                appliedCredits: credits,
                positive: false,
            }),
        };
    };

    const generateTestCaseFeedbackPair = (showDetails: boolean, name: string, message: string | undefined, credits = 0) => {
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
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase1', 'This failed.'));
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
            imports: [ArtemisTestModule],
            declarations: [
                ResultDetailComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockComponent(ChartComponent),
                MockComponent(FeedbackCollapseComponent),
                MockDirective(NgbTooltip),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
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

                comp.result = {
                    id: 89,
                    participation: {
                        id: 55,
                        exercise,
                        type: ParticipationType.PROGRAMMING,
                        participantIdentifier: 'student42',
                        repositoryUrl: 'https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42',
                    },
                } as Result;

                buildLogService = debugElement.injector.get(BuildLogService);
                resultService = debugElement.injector.get(ResultService);
                profileService = debugElement.injector.get(ProfileService);

                buildlogsStub = stub(buildLogService, 'getBuildLogs').returns(of([]));
                getFeedbackDetailsForResultStub = stub(resultService, 'getFeedbackDetailsForResult').returns(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));

                // Set profile info
                const profileInfo = new ProfileInfo();
                profileInfo.commitHashURLTemplate = commitHashURLTemplate;
                stub(profileService, 'getProfileInfo').returns(new BehaviorSubject(profileInfo));
            });
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

        expect(comp.getCommitHash()).to.equal('123456789ab');
        expect(comp.getCommitUrl()).to.equal('https://bitbucket.ase.in.tum.de/projects/somekey/repos/somekey-student42/commits/123456789ab');
    });

    it('should not try to retrieve the feedbacks from the server if provided result has feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have feedbacks', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        getFeedbackDetailsForResultStub.returns(of({ body: feedbacks } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id!, comp.result.id);
        expect(comp.filteredFeedbackList).to.have.same.deep.members(expectedItems);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and no submission was provided.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id, comp.result.id);
        expect(comp.buildLogs).to.deep.equal([]);
        expect(comp.isLoading).to.be.false;
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and a submission was provided which was marked with build failed.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(true);

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id, comp.result.id);
        expect(comp.buildLogs).to.deep.equal([]);
        expect(comp.isLoading).to.be.false;
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING', () => {
        comp.exerciseType = ExerciseType.MODELING;
        comp.result.submission = new ModelingSubmission();

        comp.ngOnInit();

        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.feedbackList).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('should not try to retrieve build logs if submission was not marked with build failed', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(false);

        comp.ngOnInit();

        expect(buildlogsStub).to.not.have.been.called;
        expect(comp.buildLogs).to.be.undefined;
        expect(comp.isLoading).to.be.false;
    });

    it('fetchBuildLogs should suppress 403 error', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 403 });
        buildlogsStub.returns(throwError(response));

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id, comp.result.id);
        expect(comp.loadingFailed).to.be.false;
        expect(comp.isLoading).to.be.false;
    });

    it('fetchBuildLogs should not suppress errors with status other than 403', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        const response = new HttpErrorResponse({ status: 500 });
        buildlogsStub.returns(throwError(response));

        comp.ngOnInit();

        expect(buildlogsStub).to.have.been.calledOnceWithExactly(comp.result.participation!.id, comp.result.id);
        expect(comp.loadingFailed).to.be.true;
        expect(comp.isLoading).to.be.false;
    });

    it('should show test names if showTestDetails is set to true', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems(true);
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.showTestDetails = true;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems);
        expect(comp.isLoading).to.be.false;
    });

    it('should filter the correct feedbacks when a filter is set', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.feedbackFilter = ['TestCase1', 'TestCase2', 'TestCase3'];

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).to.not.have.been.called;
        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems.filter((item) => item.type === FeedbackItemType.Test));
        expect(comp.isLoading).to.be.false;
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

        expectedItems.forEach((item, index) => expect(comp.getClassNameForFeedbackItem(item)).to.equal(expectedClasses[index]));
    });

    it('should calculate the correct chart values and update the score chart', () => {
        const { feedbacks, expectedItems } = generateFeedbacksAndExpectedItems(true);
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.showScoreChart = true;
        comp.showTestDetails = true;
        comp.result.feedbacks = feedbacks;

        comp.scoreChartPreset.applyTo(new ChartComponent());
        const chartSetValuesSpy = spy(comp.scoreChartPreset, 'setValues');

        comp.ngOnInit();

        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems);
        expect(comp.showScoreChartTooltip).to.equal(true);

        expect(chartSetValuesSpy).to.have.been.calledOnceWithExactly(10, 5, 6, 100, 100);
        checkChartPreset(5, 5, '10', '5 of 6');
        expect(comp.isLoading).to.be.false;

        // test score exceeding exercise maxpoints

        const feedbackPair1 = generateTestCaseFeedbackPair(true, '', '', 120);
        feedbacks.push(feedbackPair1.fb);
        expectedItems.push(feedbackPair1.item);

        chartSetValuesSpy.resetHistory();
        comp.ngOnInit();

        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems);
        expect(chartSetValuesSpy).to.have.been.calledOnceWithExactly(104, 5, 6, 100, 100);
        checkChartPreset(99, 1, '100 of 104', '1 of 6');

        // test negative > positive, limit at 0

        feedbacks.pop();
        expectedItems.pop();
        const feedbackPair2 = generateSCAFeedbackPair(true, 'Tohuwabohu', -200, 200);
        feedbacks.push(feedbackPair2.fb);
        expectedItems.push(feedbackPair2.item);

        chartSetValuesSpy.resetHistory();
        comp.ngOnInit();

        expect(comp.filteredFeedbackList).to.have.deep.members(expectedItems);
        expect(chartSetValuesSpy).to.have.been.calledOnceWithExactly(10, 22, 206, 100, 100);
        checkChartPreset(0, 10, '10', '10 of 206');
    });

    const checkChartPreset = (d1: number, d2: number, l1: string, l2: string) => {
        // @ts-ignore
        expect(comp.scoreChartPreset.datasets.length).to.equal(2);
        // @ts-ignore
        expect(comp.scoreChartPreset.datasets[0].data[0]).to.equal(d1);
        // @ts-ignore
        expect(comp.scoreChartPreset.valueLabels[0]).to.equal(l1);
        // @ts-ignore
        expect(comp.scoreChartPreset.datasets[1].data[0]).to.equal(d2);
        // @ts-ignore
        expect(comp.scoreChartPreset.valueLabels[1]).to.equal(l2);
    };
});

import { ProgrammingFeedbackItemService } from 'app/exercises/shared/feedback/item/programming/programming-feedback-item.service';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { TranslateService } from '@ngx-translate/core';
import { FeedbackItem } from 'app/exercises/shared/feedback/item/feedback-item';
import { StaticCodeAnalysisIssue } from 'app/entities/static-code-analysis-issue.model';

describe('ProgrammingFeedbackItemService', () => {
    let service: ProgrammingFeedbackItemService;

    beforeEach(() => {
        const fake = { instant: (key: string) => key };
        service = new ProgrammingFeedbackItemService(fake as TranslateService);
    });

    it('should create submission policy feedback item', () => {
        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;
        feedback.text = SUBMISSION_POLICY_FEEDBACK_IDENTIFIER;

        const expected = new FeedbackItem();
        expected.type = 'Submission Policy';
        expected.name = 'artemisApp.programmingExercise.submissionPolicy.title';
        expected.credits = 0;
        expected.positive = false;
        expected.title = '';

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create SCA feedback item', () => {
        const feedback = createSCAFeedback();

        const expected = new FeedbackItem();
        expected.credits = -10;
        expected.name = 'artemisApp.result.detail.codeIssue.name';
        expected.positive = false;
        expected.text = 'message';
        expected.title = 'artemisApp.result.detail.codeIssue.title';
        expected.type = 'Static Code Analysis';

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should create SCA feedback item with details', () => {
        const feedback = createSCAFeedback();

        const expected = new FeedbackItem();
        expected.credits = -10;
        expected.name = 'artemisApp.result.detail.codeIssue.name';
        expected.text = 'rule: message';
        expected.positive = false;
        expected.title = 'artemisApp.result.detail.codeIssue.title';
        expected.type = 'Static Code Analysis';

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should create automatic feedback item', () => {
        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;

        const expected = new FeedbackItem();
        expected.credits = 0;
        expected.name = 'artemisApp.result.detail.feedback';
        expected.type = 'Test';

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    it('should show a replacement title if automatic feedback is neither positive nor negative', () => {
        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;
        feedback.text = 'automaticTestCase1';
        feedback.positive = undefined;
        feedback.credits = 0.3;

        const expected = new FeedbackItem();
        expected.name = 'artemisApp.result.detail.test.name';
        expected.credits = 0.3;
        expected.title = 'artemisApp.result.detail.test.noInfo';
        expected.type = 'Test';
        expected.text = undefined;

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should create automatic feedback item with details', () => {
        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;

        const expected = new FeedbackItem();
        expected.credits = 0;
        expected.type = 'Test';
        expected.name = 'artemisApp.result.detail.test.name';
        expected.title = 'artemisApp.result.detail.test.noInfo';

        expect(service.create([feedback], true)).toEqual([expected]);
    });

    it('should create grading instruction feedback item', () => {
        const feedback = new Feedback();
        feedback.type = FeedbackType.MANUAL;

        const expected = new FeedbackItem();
        expected.credits = 0;
        expected.name = 'artemisApp.result.detail.feedback';
        expected.type = 'Reviewer';

        expect(service.create([feedback], false)).toEqual([expected]);
    });

    const createSCAFeedback = () => {
        const scaIssue = new StaticCodeAnalysisIssue();
        scaIssue.rule = 'rule';
        scaIssue.message = 'message';
        scaIssue.penalty = 10;

        const feedback = new Feedback();
        feedback.type = FeedbackType.AUTOMATIC;
        feedback.text = STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER;
        feedback.detailText = JSON.stringify(scaIssue);

        return feedback;
    };
});

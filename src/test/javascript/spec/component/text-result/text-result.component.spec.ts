import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockDirective, MockPipe } from 'ng-mocks';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Result } from 'app/entities/result.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';
import { TextResultBlock } from 'app/exercises/text/participate/text-result/text-result-block';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { faCheck, faCircle, faTimes } from '@fortawesome/free-solid-svg-icons';

describe('TextResultComponent', () => {
    let fixture: ComponentFixture<TextResultComponent>;
    let component: TextResultComponent;

    const feedbacks = [
        {
            id: 1,
            detailText: 'feedback1',
            credits: 1.5,
            reference: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98f',
        } as Feedback,
        {
            id: 3,
            detailText: 'feedback3',
            credits: 1,
            reference: 'exercise',
        } as Feedback,
        {
            id: 2,
            detailText: 'feedback2',
            credits: 0,
            reference: 'ed462aaf735fe740a260660cbcbfbcc0ee66f98a',
        } as Feedback,
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextResultComponent, MockDirective(MockHasAnyAuthorityDirective), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TextResultComponent);
                component = fixture.componentInstance;
            });
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should convert text to result blocks', () => {
        const result = new Result();
        const submission = new TextSubmission();
        submission.text = 'submission text for a text exercise';
        const blocks = [
            {
                id: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98f',
                text: 'submission',
                startIndex: 0,
                endIndex: 10,
            } as TextBlock,
            {
                id: 'ed462aaf735fe740a260660cbcbfbcc0ee66f98a',
                text: ' text',
                startIndex: 10,
                endIndex: 15,
            } as TextBlock,
            {
                id: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98GGG',
                text: ' for a',
                startIndex: 15,
                endIndex: 21,
            } as TextBlock,
            {
                id: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98GGH',
                text: ' exercise',
                startIndex: 21,
                endIndex: 29,
            } as TextBlock,
        ];
        submission.blocks = blocks;
        result.submission = submission;
        result.feedbacks = feedbacks;

        component.result = result;

        expect(component.textResults).toHaveLength(4);
    });

    it('should repeat steps for each credit', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        const textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(component.repeatForEachCredit(textResultBlock)).toEqual([1, 1]);
    });

    it('should translate credits', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        let textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(component.creditsTranslationForTextResultBlock(textResultBlock)).toBe('artemisApp.assessment.detail.points.many');

        textResultBlock = new TextResultBlock(textBlock, feedbacks[1]);

        expect(component.creditsTranslationForTextResultBlock(textResultBlock)).toBe('artemisApp.assessment.detail.points.one');
    });

    it('should test result block methods', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        textBlock.startIndex = 0;
        textBlock.endIndex = 5;
        let textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(textResultBlock).toHaveLength(5);
        expect(textResultBlock.cssClass).toBe('text-with-feedback positive-feedback');
        expect(textResultBlock.icon).toBe(faCheck);
        expect(textResultBlock.iconCssClass).toBe('feedback-icon positive-feedback');
        expect(textResultBlock.feedbackCssClass).toBe('alert alert-success');

        textResultBlock = new TextResultBlock(textBlock, feedbacks[2]);

        expect(textResultBlock.cssClass).toBe('text-with-feedback neutral-feedback');
        expect(textResultBlock.icon).toBe(faCircle);
        expect(textResultBlock.iconCssClass).toBe('feedback-icon neutral-feedback');
        expect(textResultBlock.feedbackCssClass).toBe('alert alert-secondary');

        const feedback = {
            id: 3,
            detailText: 'feedback5',
            credits: -1,
            reference: 'exercise',
        } as Feedback;

        textResultBlock = new TextResultBlock(textBlock, feedback);

        expect(textResultBlock.cssClass).toBe('text-with-feedback negative-feedback');
        expect(textResultBlock.icon).toBe(faTimes);
        expect(textResultBlock.iconCssClass).toBe('feedback-icon negative-feedback');
        expect(textResultBlock.feedbackCssClass).toBe('alert alert-danger');
    });

    it('should display the feedback text properly', () => {
        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 0,
        } as GradingInstruction;
        const feedback = feedbacks[0];

        let textToBeDisplayed = component.buildFeedbackTextForReview(feedback);
        expect(textToBeDisplayed).toBe(feedback.detailText);

        feedback.gradingInstruction = gradingInstruction;
        textToBeDisplayed = component.buildFeedbackTextForReview(feedback);
        expect(textToBeDisplayed).toEqual(gradingInstruction.feedback + '<br>' + feedback.detailText);
    });

    it('should mark the subsequent feedback', () => {
        const result = new Result();
        const submission = new TextSubmission();
        submission.text = 'submission text';

        const gradingInstruction = {
            id: 1,
            credits: 1,
            gradingScale: 'scale',
            instructionDescription: 'description',
            feedback: 'instruction feedback',
            usageCount: 1,
        } as GradingInstruction;

        const blocks = [
            {
                id: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98f',
                text: 'submission',
                startIndex: 0,
                endIndex: 10,
            } as TextBlock,
            {
                id: 'ed462aaf735fe740a260660cbcbfbcc0ee66f98a',
                text: ' text',
                startIndex: 10,
                endIndex: 15,
            } as TextBlock,
        ];

        const feedback = [
            {
                id: 1,
                detailText: 'feedback1',
                credits: 1,
                reference: 'ed462aaf735fe740a260660cbbbfbcc0ee66f98f',
                gradingInstruction,
            } as Feedback,
            {
                id: 2,
                detailText: 'feedback2',
                credits: 1,
                reference: 'ed462aaf735fe740a260660cbcbfbcc0ee66f98a',
                gradingInstruction,
            } as Feedback,
        ];

        submission.blocks = blocks;
        result.submission = submission;
        result.feedbacks = feedback;

        component.result = result;

        expect(component.textResults).toHaveLength(2);
        expect(component.textResults[0].feedback).toBeDefined();
        expect(component.textResults[0].feedback!.isSubsequent).toBeUndefined();
        expect(component.textResults[1].feedback).toBeDefined();
        expect(component.textResults[1].feedback!.isSubsequent).toBeTrue();
    });
});

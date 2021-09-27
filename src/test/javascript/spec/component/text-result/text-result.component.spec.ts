import * as chai from 'chai';
import sinonChai from 'sinon-chai';
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

chai.use(sinonChai);
const expect = chai.expect;

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
        expect(component).to.be.ok;
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

        expect(component.textResults.length).to.equal(4);
    });

    it('should repeat steps for each credit', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        const textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(component.repeatForEachCredit(textResultBlock)).to.deep.equal([1, 1]);
    });

    it('should translate credits', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        let textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(component.creditsTranslationForTextResultBlock(textResultBlock)).to.equal('artemisApp.textAssessment.detail.credits.many');

        textResultBlock = new TextResultBlock(textBlock, feedbacks[1]);

        expect(component.creditsTranslationForTextResultBlock(textResultBlock)).to.equal('artemisApp.textAssessment.detail.credits.one');
    });

    it('should test result block methods', () => {
        const textBlock = new TextBlock();
        textBlock.text = 'this is a text block';
        textBlock.startIndex = 0;
        textBlock.endIndex = 5;
        let textResultBlock = new TextResultBlock(textBlock, feedbacks[0]);

        expect(textResultBlock.length).to.equal(5);
        expect(textResultBlock.cssClass).to.equal('text-with-feedback positive-feedback');
        expect(textResultBlock.icon).to.equal('check');
        expect(textResultBlock.iconCssClass).to.equal('feedback-icon positive-feedback');
        expect(textResultBlock.feedbackCssClass).to.equal('alert alert-success');

        textResultBlock = new TextResultBlock(textBlock, feedbacks[2]);

        expect(textResultBlock.cssClass).to.equal('text-with-feedback neutral-feedback');
        expect(textResultBlock.icon).to.equal('circle');
        expect(textResultBlock.iconCssClass).to.equal('feedback-icon neutral-feedback');
        expect(textResultBlock.feedbackCssClass).to.equal('alert alert-secondary');

        const feedback = {
            id: 3,
            detailText: 'feedback5',
            credits: -1,
            reference: 'exercise',
        } as Feedback;

        textResultBlock = new TextResultBlock(textBlock, feedback);

        expect(textResultBlock.cssClass).to.equal('text-with-feedback negative-feedback');
        expect(textResultBlock.icon).to.equal('times');
        expect(textResultBlock.iconCssClass).to.equal('feedback-icon negative-feedback');
        expect(textResultBlock.feedbackCssClass).to.equal('alert alert-danger');
    });
});

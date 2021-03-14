import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, fakeAsync, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../test.module';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockDirective, MockPipe } from 'ng-mocks';
import { MockHasAnyAuthorityDirective } from '../../helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe.ts';
import { TextResultComponent } from 'app/exercises/text/participate/text-result/text-result.component';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Result } from 'app/entities/result.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('TextResultComponent', () => {
    let fixture: ComponentFixture<TextResultComponent>;
    let component: TextResultComponent;

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

    afterEach(() => {
        // jest.clearAllMocks();
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
                credits: 0.5,
                reference: 'exercise',
            } as Feedback,
            {
                id: 2,
                detailText: 'feedback2',
                credits: 2.5,
                reference: 'ed462aaf735fe740a260660cbcbfbcc0ee66f98a',
            } as Feedback,
        ];
        result.feedbacks = feedbacks;

        component.result = result;

        expect(component.textResults.length).to.equal(4);
    });

    it('should ', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });
});

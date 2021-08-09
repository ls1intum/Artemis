import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { MockMetisService } from '../../../../../helpers/mocks/service/mock-metis-service.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostReactionsBarComponent', () => {
    let component: AnswerPostReactionsBarComponent;
    let fixture: ComponentFixture<AnswerPostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUserAuthorityStub: SinonStub;

    const answerPost = {
        id: 1,
        content: 'post without reaction',
    } as AnswerPost;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [OverlayModule],
            providers: [{ provide: MetisService, useClass: MockMetisService }],
            declarations: [AnswerPostReactionsBarComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostReactionsBarComponent);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                metisServiceUserAuthorityStub = stub(metisService, 'metisUserIsAtLeastTutorInCourse');
                component.posting = answerPost;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and reactions correctly', () => {
        metisServiceUserAuthorityStub.returns(true);
        component.ngOnInit();
        fixture.detectChanges();
        const triggerEmojiMartButton = getElement(debugElement, '.trigger-emoji-mart-selector');
        expect(triggerEmojiMartButton).to.exist;
        const reaction = getElement(debugElement, 'ngx-emoji');
        expect(reaction).to.not.exist;
        expect(component.reactionCountMap).to.deep.equal({});
    });

    it('should increase count for an existing reaction when clicked by the user for the first time', () => {
        // add one existing reaction, made by another user
        component.reactionCountMap = {
            smile: {
                count: 1,
                hasReacted: false,
            },
        };
        component.ngOnInit();
        expect(component.showReactionSelector).to.be.equal(false);
        const triggerEmojiMartButton = getElement(debugElement, '.trigger-emoji-mart-selector');
        triggerEmojiMartButton.click();
        fixture.detectChanges();
        expect(component.showReactionSelector).to.be.equal(true);
        // add reaction to existing reaction
        // in map: check emojiId present, check count 1 -> 2, check reacted flag
        // check emoji displayed
    });

    it('should decrease count for an existing reaction when clicked by the user that already reacted');
    // remove existing reaction
    // check count 2 -> 1 in map
    // check emoji displayed
});

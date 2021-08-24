import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, getTestBed, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { CUSTOM_ELEMENTS_SCHEMA } from '@angular/core';
import * as sinon from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { User } from 'app/core/user/user.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';

chai.use(sinonChai);
const expect = chai.expect;

describe('AnswerPostReactionsBarComponent', () => {
    let component: AnswerPostReactionsBarComponent;
    let injector: TestBed;
    let fixture: ComponentFixture<AnswerPostReactionsBarComponent>;
    let metisService: MetisService;
    let answerPost: AnswerPost;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    const user = { id: 1, name: 'username', login: 'login' } as User;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, OverlayModule, EmojiModule],
            schemas: [CUSTOM_ELEMENTS_SCHEMA],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
            ],
            declarations: [AnswerPostReactionsBarComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostReactionsBarComponent);
                injector = getTestBed();
                metisService = injector.get(MetisService);
                component = fixture.componentInstance;
                answerPost = new AnswerPost();
                answerPost.id = 1;
                answerPost.author = user;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = user;
                reactionToDelete.answerPost = answerPost;
                answerPost.reactions = [reactionToDelete];
                component.posting = answerPost;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = sinon.spy(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.answerPost = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionSpy).to.have.been.calledWith(reactionToCreate);
        expect(component.showReactionSelector).to.be.equal(false);
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = sinon.spy(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceCreateReactionSpy).to.have.been.calledWith(reactionToDelete);
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.ngOnInit();
        const addOrRemoveSpy = sinon.spy(component, 'addOrRemoveReaction');
        component.updateReaction(reactionToDelete.emojiId!);
        expect(addOrRemoveSpy).to.have.been.calledWith(reactionToDelete.emojiId!);
    });
});

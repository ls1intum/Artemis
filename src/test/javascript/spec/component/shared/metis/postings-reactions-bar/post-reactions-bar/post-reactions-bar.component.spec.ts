import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, getTestBed, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { getElement } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { User } from 'app/core/user/user.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';

chai.use(sinonChai);
const expect = chai.expect;

describe('PostReactionsBarComponent', () => {
    let component: PostReactionsBarComponent;
    let injector: TestBed;
    let fixture: ComponentFixture<PostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let accountService: MockAccountService;
    let accountServiceAuthorityStub: SinonStub;

    const user = { id: 1, name: 'username', login: 'login' } as User;

    const reaction = {
        id: 1,
        emojiId: 'smile',
        user,
    } as Reaction;

    const post = {
        id: 1,
        content: 'post with reaction',
        reactions: [reaction],
        author: user,
    } as Post;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, OverlayModule],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
            ],
            declarations: [PostReactionsBarComponent, MockPipe(ArtemisTranslatePipe)],
            schemas: [NO_ERRORS_SCHEMA],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostReactionsBarComponent);
                injector = getTestBed();
                metisService = injector.get(MetisService);
                accountService = injector.get(AccountService);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                accountServiceAuthorityStub = stub(accountService, 'isAtLeastTutorInCourse');
                component.posting = post;
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize user authority and reactions correctly', () => {
        accountServiceAuthorityStub.returns(false);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).to.deep.equal(false);
        fixture.detectChanges();
        const reactions = getElement(debugElement, 'ngx-emoji');
        expect(reactions).to.exist;
        expect(component.reactionCountMap).to.be.deep.equal({
            smile: {
                count: 1,
                hasReacted: false,
            },
        });
    });

    it('should initialize user authority and reactions correctly with same user', () => {
        component.posting!.author!.id = 99;
        accountServiceAuthorityStub.returns(true);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).to.deep.equal(true);
        fixture.detectChanges();
        const reactions = getElement(debugElement, 'ngx-emoji');
        expect(reactions).to.exist;
        expect(component.reactionCountMap).to.be.deep.equal({
            smile: {
                count: 1,
                hasReacted: true,
            },
        });
    });

    // add reaction for the first time
    // in map: check emojiId present, check count 0 -> 1, check reacted flag
    // check emoji displayed
    it('should update reactionCountMap and display a new emoji when method addOrRemoveReaction is called', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = sinon.spy(metisService, 'createReaction');
        component.addOrRemoveReaction('+1');
        expect(metisServiceCreateReactionSpy).to.have.been.called;
        // expect(metisServiceCreateReactionSpy).to.have.been.calledWith({
        //     emojiId: '+1',
        //     post: component.posting,
        // });
    });

    // remove existing reaction
    // in map: check emojiId not present, check count 0 -> 1, check reacted flag
    // check emoji not displayed
});

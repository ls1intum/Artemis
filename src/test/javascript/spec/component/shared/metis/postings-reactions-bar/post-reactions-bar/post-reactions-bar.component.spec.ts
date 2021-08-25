import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, getTestBed, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import * as sinon from 'sinon';
import { SinonStub, stub } from 'sinon';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe } from 'ng-mocks';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/postings-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { User } from 'app/core/user/user.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { PickerModule } from '@ctrl/ngx-emoji-mart';

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
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    const user = { id: 1, name: 'username', login: 'login' } as User;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule)],
            providers: [
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
            ],
            declarations: [PostReactionsBarComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FaIconComponent), MockDirective(NgbTooltip)],
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
                post = new Post();
                post.id = 1;
                post.author = user;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = user;
                reactionToDelete.post = post;
                post.reactions = [reactionToDelete];
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
        const reactions = getElements(debugElement, 'ngx-emoji');
        // emojis to be displayed it the user reaction
        expect(reactions).to.have.length(1);
        expect(component.reactionCountMap).to.be.deep.equal({
            smile: {
                count: 1,
                hasReacted: true,
            },
        });
    });

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = sinon.spy(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.post = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionSpy).to.have.been.calledWith(reactionToCreate);
        expect(component.showReactionSelector).to.be.equal(false);
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceDeleteReactionSpy = sinon.spy(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceDeleteReactionSpy).to.have.been.calledWith(reactionToDelete);
        expect(component.showReactionSelector).to.be.equal(false);
    });
});

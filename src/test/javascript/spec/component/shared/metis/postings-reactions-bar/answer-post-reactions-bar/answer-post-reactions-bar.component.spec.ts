import { OverlayModule } from '@angular/cdk/overlay';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router } from '@angular/router';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { AnswerPostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/answer-post-reactions-bar/answer-post-reactions-bar.component';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { MockLocalStorageService } from '../../../../../helpers/mocks/service/mock-local-storage.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { MockTranslateService, TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { metisCourse, metisUser1, post } from '../../../../../helpers/sample/metis-sample-data';

describe('AnswerPostReactionsBarComponent', () => {
    let component: AnswerPostReactionsBarComponent;
    let fixture: ComponentFixture<AnswerPostReactionsBarComponent>;
    let metisService: MetisService;
    let answerPost: AnswerPost;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule)],
            declarations: [AnswerPostReactionsBarComponent, TranslatePipeMock, MockPipe(ReactingUsersOnPostingPipe), MockComponent(FaIconComponent), MockDirective(NgbTooltip)],
            providers: [
                MockProvider(SessionStorageService),
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AnswerPostReactionsBarComponent);
                metisService = TestBed.inject(MetisService);
                component = fixture.componentInstance;
                answerPost = new AnswerPost();
                answerPost.id = 1;
                answerPost.author = metisUser1;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = metisUser1;
                reactionToDelete.answerPost = answerPost;
                answerPost.reactions = [reactionToDelete];
                component.posting = answerPost;
                metisService.setCourse(metisCourse);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = jest.spyOn(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.answerPost = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionSpy).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector).toBeFalse();
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionSpy = jest.spyOn(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceCreateReactionSpy).toHaveBeenCalledWith(reactionToDelete);
    });

    it('should invoke metis service method with own reaction to remove it', () => {
        component.ngOnInit();
        const addOrRemoveSpy = jest.spyOn(component, 'addOrRemoveReaction');
        component.updateReaction(reactionToDelete.emojiId!);
        expect(addOrRemoveSpy).toHaveBeenCalledWith(reactionToDelete.emojiId!);
    });

    it('answer now button should be invisible if answer is not the last one', () => {
        component.posting = post;
        component.isLastAnswer = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn'));
        expect(answerNowButton).toBeNull();
    });

    it('answer now button should be visible if answer is the last one', () => {
        component.posting = post;
        component.isLastAnswer = true;
        component.ngOnInit();
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('reply');
    });
});

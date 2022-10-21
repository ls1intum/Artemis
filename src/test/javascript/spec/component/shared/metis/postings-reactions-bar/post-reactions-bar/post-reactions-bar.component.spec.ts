import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { EmojiData, EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { MockTranslateService, TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { metisCourse, metisPostExerciseUser1, metisUser1, sortedAnswerArray } from '../../../../../helpers/sample/metis-sample-data';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';

describe('PostReactionsBarComponent', () => {
    let component: PostReactionsBarComponent;
    let fixture: ComponentFixture<PostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let metisServiceUpdateDisplayPriorityMock: jest.SpyInstance;
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    const SPEECH_BALLOON_UNICODE = '1F4AC';
    const ARCHIVE_EMOJI_UNICODE = '1F4C2';
    const PIN_EMOJI_UNICODE = '1F4CC';
    const HEAVY_MULTIPLICATION_UNICODE = '2716';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [HttpClientTestingModule, MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule)],
            declarations: [
                PostReactionsBarComponent,
                TranslatePipeMock,
                MockPipe(ReactingUsersOnPostingPipe),
                MockDirective(NgbTooltip),
                MockComponent(FaIconComponent),
                EmojiComponent,
            ],
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
                fixture = TestBed.createComponent(PostReactionsBarComponent);
                metisService = TestBed.inject(MetisService);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisServiceUpdateDisplayPriorityMock = jest.spyOn(metisService, 'updatePostDisplayPriority');
                post = new Post();
                post.id = 1;
                post.author = metisUser1;
                post.displayPriority = DisplayPriority.NONE;
                component.sortedAnswerPosts = sortedAnswerArray;
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = metisUser1;
                reactionToDelete.post = post;
                post.reactions = [reactionToDelete];
                component.posting = post;
                metisService.setCourse(metisCourse);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize user authority and reactions correctly', () => {
        metisCourse.isAtLeastTutor = false;
        metisService.setCourse(metisCourse);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).toBeFalse();
        fixture.detectChanges();
        const reaction = getElement(debugElement, 'ngx-emoji');
        expect(reaction).toBeDefined();
        expect(component.reactionMetaDataMap).toEqual({
            smile: {
                count: 1,
                hasReacted: false,
                reactingUsers: ['username1'],
            },
        });
    });

    it('should initialize user authority and reactions correctly with same user', () => {
        component.posting!.author!.id = 99;
        metisCourse.isAtLeastTutor = true;
        metisService.setCourse(metisCourse);
        component.ngOnInit();
        expect(component.currentUserIsAtLeastTutor).toBeTrue();
        fixture.detectChanges();
        const reactions = getElements(debugElement, 'jhi-emoji');
        // emojis to be displayed it the user reaction, the pin, archive and the show answers toggle emoji
        expect(reactions).toHaveLength(4);
        expect(component.reactionMetaDataMap).toEqual({
            smile: {
                count: 1,
                hasReacted: true,
                reactingUsers: [PLACEHOLDER_USER_REACTED],
            },
        });
        // set correct tooltips for tutor and post that is not pinned and not archived
        expect(component.archiveTooltip).toBe('artemisApp.metis.archivePostTutorTooltip');
        expect(component.pinTooltip).toBe('artemisApp.metis.pinPostTutorTooltip');
    });

    it.each`
        input                           | expect
        ${PIN_EMOJI_UNICODE}            | ${false}
        ${ARCHIVE_EMOJI_UNICODE}        | ${false}
        ${SPEECH_BALLOON_UNICODE}       | ${false}
        ${HEAVY_MULTIPLICATION_UNICODE} | ${false}
    `('should remove unavailable reactions from the emoji selector', (param: { input: string | EmojiData; expect: boolean }) => {
        expect(component.emojisToShowFilter(param.input)).toBe(param.expect);
    });

    it('should invoke metis service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceCreateReactionMock = jest.spyOn(metisService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.post = component.posting;
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionMock).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        component.posting!.author!.id = 99;
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceDeleteReactionMock = jest.spyOn(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceDeleteReactionMock).toHaveBeenCalledWith(reactionToDelete);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method when pin icon is toggled', () => {
        metisCourse.isAtLeastTutor = true;
        metisService.setCourse(metisCourse);
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin');
        pinEmoji.click();
        component.posting.displayPriority = DisplayPriority.PINNED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting.id!, DisplayPriority.PINNED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is pinned and not archived
        expect(component.pinTooltip).toBe('artemisApp.metis.removePinPostTutorTooltip');
        expect(component.archiveTooltip).toBe('artemisApp.metis.archivePostTutorTooltip');
    });

    it('should invoke metis service method when archive icon is toggled', () => {
        metisCourse.isAtLeastTutor = true;
        metisService.setCourse(metisCourse);
        component.ngOnInit();
        fixture.detectChanges();
        const archiveEmoji = getElement(debugElement, '.archive');
        archiveEmoji.click();
        component.posting.displayPriority = DisplayPriority.ARCHIVED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting.id!, DisplayPriority.ARCHIVED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is archived and not pinned
        expect(component.pinTooltip).toBe('artemisApp.metis.pinPostTutorTooltip');
        expect(component.archiveTooltip).toBe('artemisApp.metis.removeArchivePostTutorTooltip');
    });

    it('should show non-clickable pin emoji with correct tooltip for student when post is pinned', () => {
        metisCourse.isAtLeastTutor = false;
        metisService.setCourse(metisCourse);
        component.posting.displayPriority = DisplayPriority.PINNED;
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin.reaction-button--not-hoverable');
        expect(pinEmoji).toBeDefined();
        pinEmoji.click();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is pinned
        expect(component.pinTooltip).toBe('artemisApp.metis.pinnedPostTooltip');
    });

    it('should show non-clickable archive emoji with correct tooltip for student when post is archived', () => {
        metisCourse.isAtLeastTutor = false;
        metisService.setCourse(metisCourse);
        component.posting.displayPriority = DisplayPriority.ARCHIVED;
        component.ngOnInit();
        fixture.detectChanges();
        const archiveEmoji = getElement(debugElement, '.archive.reaction-button--not-hoverable');
        expect(archiveEmoji).toBeDefined();
        archiveEmoji.click();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is archived
        expect(component.archiveTooltip).toBe('artemisApp.metis.archivedPostTooltip');
    });

    it('start discussion button should be visible if post does not yet have any answers', () => {
        component.posting = post;
        component.sortedAnswerPosts = [];
        fixture.detectChanges();
        const startDiscussion = fixture.debugElement.query(By.css('.reply-btn')).nativeElement;
        expect(startDiscussion.innerHTML).toContain('reply');
    });

    it('should display button to show single answer', () => {
        component.posting = post;
        component.sortedAnswerPosts = [metisPostExerciseUser1];
        component.showAnswers = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('showSingleAnswer');
    });

    it('should display button to show multiple answers', () => {
        component.posting = post;
        component.showAnswers = false;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('showMultipleAnswers');
    });

    it('should display button to collapse answers', () => {
        component.posting = post;
        component.showAnswers = true;
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.collapse-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('collapseAnswers');
    });
});

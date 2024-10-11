import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetisService } from 'app/shared/metis/metis.service';
import { DebugElement } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { getElement, getElements } from '../../../../../helpers/utils/general.utils';
import { PostReactionsBarComponent } from 'app/shared/metis/posting-reactions-bar/post-reactions-bar/post-reactions-bar.component';
import { OverlayModule } from '@angular/cdk/overlay';
import { Reaction } from 'app/entities/metis/reaction.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { MockReactionService } from '../../../../../helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../../../helpers/mocks/service/mock-account.service';
import { EmojiData, EmojiModule } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PickerModule } from '@ctrl/ngx-emoji-mart';
import { DisplayPriority, UserRole } from 'app/shared/metis/metis.util';
import { MockTranslateService, TranslatePipeMock } from '../../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from '../../../../../helpers/mocks/mock-router';
import { MockLocalStorageService } from '../../../../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { By } from '@angular/platform-browser';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { metisAnnouncement, metisCourse, metisPostExerciseUser1, metisPostInChannel, metisUser1, sortedAnswerArray } from '../../../../../helpers/sample/metis-sample-data';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../../../../helpers/mocks/service/mock-notification.service';
import { ConversationDTO, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { User } from 'app/core/user/user.model';
import { provideHttpClient } from '@angular/common/http';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';

describe('PostReactionsBarComponent', () => {
    let component: PostReactionsBarComponent;
    let fixture: ComponentFixture<PostReactionsBarComponent>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let accountService: AccountService;
    let metisServiceUpdateDisplayPriorityMock: jest.SpyInstance;
    let metisServiceUserIsAtLeastTutorStub: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceUserIsAuthorOfPostingStub: jest.SpyInstance;
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;

    const SPEECH_BALLOON_UNICODE = '1F4AC';
    const ARCHIVE_EMOJI_UNICODE = '1F4C2';
    const PIN_EMOJI_UNICODE = '1F4CC';
    const HEAVY_MULTIPLICATION_UNICODE = '2716';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(OverlayModule), MockModule(EmojiModule), MockModule(PickerModule), MockDirective(NgbTooltip)],
            declarations: [
                PostReactionsBarComponent,
                TranslatePipeMock,
                MockPipe(ReactingUsersOnPostingPipe),
                MockComponent(FaIconComponent),
                MockComponent(PostCreateEditModalComponent),
                EmojiComponent,
                MockComponent(ConfirmIconComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(SessionStorageService),
                { provide: MetisService, useClass: MetisService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostReactionsBarComponent);
                metisService = TestBed.inject(MetisService);
                accountService = TestBed.inject(AccountService);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisServiceUpdateDisplayPriorityMock = jest.spyOn(metisService, 'updatePostDisplayPriority');
                metisServiceUserIsAtLeastTutorStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserIsAtLeastInstructorStub = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceUserIsAuthorOfPostingStub = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
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

    function getEditButton(): DebugElement | null {
        return debugElement.query(By.css('button.reaction-button.clickable.px-2.fs-small.edit'));
    }

    function getDeleteButton(): DebugElement | null {
        return debugElement.query(By.css('jhi-confirm-icon'));
    }

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

    it('should invoke metis service when delete icon is clicked', () => {
        component.readOnlyMode = false;
        component.previewMode = false;
        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);
        const deletePostSpy = jest.spyOn(metisService, 'deletePost');
        component.posting = { id: 1 } as Post;

        component.ngOnInit();
        fixture.detectChanges();
        const confirmIconDebugElement = debugElement.query(By.directive(ConfirmIconComponent));
        expect(confirmIconDebugElement).not.toBeNull();

        confirmIconDebugElement.componentInstance.confirmEvent.emit();
        fixture.detectChanges();

        expect(deletePostSpy).toHaveBeenCalledOnce();
        expect(deletePostSpy).toHaveBeenCalledWith(component.posting);
    });

    it('should display edit and delete options to the author when not in read-only or preview mode', () => {
        component.readOnlyMode = false;
        component.previewMode = false;
        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);
        component.posting = { id: 1, title: 'Test Post' } as Post;

        component.ngOnInit();
        fixture.detectChanges();

        expect(getDeleteButton()).not.toBeNull();
        expect(getEditButton()).not.toBeNull();
    });

    it('should display edit and delete options to user with channel moderation rights when not the author', () => {
        component.readOnlyMode = false;
        component.previewMode = false;
        component.isEmojiCount = false;

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(false);
        jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse').mockReturnValue(false);
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.detectChanges();

        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display edit and delete options when user is not the author and lacks permissions', () => {
        component.readOnlyMode = false;
        component.previewMode = false;
        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(false);
        jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse').mockReturnValue(false);
        component.posting = { conversation: { isCourseWide: false } } as Post;

        component.ngOnInit();
        fixture.detectChanges();

        expect(debugElement.query(By.css('fa-icon[icon="pencil-alt"]'))).toBeNull();
        expect(debugElement.query(By.directive(ConfirmIconComponent))).toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is in course-wide channel', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        component.posting = { ...metisPostInChannel };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should display edit and delete options to instructor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        component.posting = metisAnnouncement;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display edit and delete options to instructor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        component.posting = { ...metisPostInChannel };
        component.posting.authorRole = UserRole.USER;
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it.each([
        { type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO,
        { type: ConversationType.GROUP_CHAT, creator: { id: 99 } },
        { type: ConversationType.ONE_TO_ONE },
    ])('should initialize user authority and reactions correctly with same user', (dto: ConversationDTO) => {
        component.posting!.author!.id = 99;
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(dto);
        jest.spyOn(accountService, 'userIdentity', 'get').mockReturnValue({ id: 99 } as User);

        reactionToDelete.user = { id: 99 } as User;
        post.reactions = [reactionToDelete];
        component.posting = post;

        component.ngOnInit();
        fixture.detectChanges();
        const reactions = getElements(debugElement, 'jhi-emoji');
        expect(reactions).toHaveLength(2);
        expect(component.reactionMetaDataMap).toEqual({
            smile: {
                count: 1,
                hasReacted: true,
                reactingUsers: [PLACEHOLDER_USER_REACTED],
            },
        });
        expect(component.pinTooltip).toBe('artemisApp.metis.pinPostTooltip');
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
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue({ type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO);
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin');
        pinEmoji.click();
        component.posting.displayPriority = DisplayPriority.PINNED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting.id!, DisplayPriority.PINNED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is pinned and not archived
        expect(component.pinTooltip).toBe('artemisApp.metis.removePinPostTooltip');
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

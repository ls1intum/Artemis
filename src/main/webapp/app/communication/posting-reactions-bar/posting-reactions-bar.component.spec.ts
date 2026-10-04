import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CommunicationService } from 'app/communication/service/communication.service';
import { DebugElement } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ReactionService } from 'app/communication/service/reaction.service';
import { MockReactionService } from 'test/helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DisplayPriority, UserRole } from 'app/communication/communication.util';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { By } from '@angular/platform-browser';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/foundation/pipes/reacting-users-on-posting.pipe';
import {
    communicationAnnouncement,
    communicationCourse,
    communicationPostExerciseUser1,
    communicationPostExerciseUser2,
    communicationPostInChannel,
    communicationResolvingAnswerPostUser1,
    communicationUser1,
    sortedAnswerArray,
    unApprovedAnswerPost1,
} from 'test/helpers/sample/communication-sample-data';
import { EmojiComponent } from 'app/communication/emoji/emoji.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { User } from 'app/account/user/user.model';
import { provideHttpClient } from '@angular/common/http';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { Subject, of } from 'rxjs';
import { MockCourseConversationsService } from 'test/helpers/mocks/service/mock-course-conversations.service';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { DialogService } from 'primeng/dynamicdialog';

describe('PostingReactionsBarComponent', () => {
    let component: PostingReactionsBarComponent<Posting>;
    let fixture: ComponentFixture<PostingReactionsBarComponent<Posting>>;
    let debugElement: DebugElement;
    let communicationService: CommunicationService;
    let accountService: AccountService;
    let courseSidebarService: CourseSidebarService;
    let reloadSidebarSpy: ReturnType<typeof vi.spyOn>;
    let communicationServiceUpdateDisplayPriorityMock: ReturnType<typeof vi.spyOn>;
    let communicationServiceUserIsAtLeastTutorStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceUserIsAtLeastInstructorStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceUserIsAuthorOfPostingStub: ReturnType<typeof vi.spyOn>;
    let communicationServiceUpdateAnswerPostMock: ReturnType<typeof vi.spyOn>;
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;
    let consoleErrorSpy: ReturnType<typeof vi.spyOn>;
    let createForwardedMessagesSpy: ReturnType<typeof vi.spyOn>;

    const SPEECH_BALLOON_UNICODE = '1F4AC';
    const ARCHIVE_EMOJI_UNICODE = '1F4C2';
    const PIN_EMOJI_UNICODE = '1F4CC';
    const HEAVY_MULTIPLICATION_UNICODE = '2716';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                MockDirective(NgbTooltip),
                PostingReactionsBarComponent,
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
                MockProvider(CourseSidebarService),
                { provide: CommunicationService, useClass: CommunicationService },
                { provide: ReactionService, useClass: MockReactionService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: CourseConversationsService, useClass: MockCourseConversationsService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: DialogService, useValue: { open: vi.fn() } },
            ],
        });
        TestBed.overrideComponent(PostingReactionsBarComponent, {
            remove: { imports: [PostCreateEditModalComponent] },
            add: { imports: [MockComponent(PostCreateEditModalComponent)] },
        });
        fixture = TestBed.createComponent(PostingReactionsBarComponent);
        communicationService = TestBed.inject(CommunicationService);
        accountService = TestBed.inject(AccountService);
        courseSidebarService = TestBed.inject(CourseSidebarService);
        reloadSidebarSpy = vi.spyOn(courseSidebarService, 'reloadSidebar');
        debugElement = fixture.debugElement;
        component = fixture.componentInstance;
        communicationServiceUpdateDisplayPriorityMock = vi.spyOn(communicationService, 'updatePostDisplayPriority');
        communicationServiceUserIsAtLeastTutorStub = vi.spyOn(communicationService, 'currentUserIsAtLeastTutorInCourse');
        communicationServiceUserIsAtLeastInstructorStub = vi.spyOn(communicationService, 'currentUserIsAtLeastInstructorInCourse');
        communicationServiceUserIsAuthorOfPostingStub = vi.spyOn(communicationService, 'currentUserIsAuthorOfPosting');
        communicationServiceUpdateAnswerPostMock = vi.spyOn(communicationService, 'updateAnswerPost');
        vi.spyOn(communicationService, 'getUser').mockReturnValue(communicationUser1);
        consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
        createForwardedMessagesSpy = vi.spyOn(communicationService, 'createForwardedMessages');
        post = new Post();
        post.id = 1;
        post.author = communicationUser1;
        post.displayPriority = DisplayPriority.NONE;
        fixture.componentRef.setInput('sortedAnswerPosts', sortedAnswerArray);
        fixture.componentRef.setInput('posting', post);
        reactionToDelete = new Reaction();
        reactionToDelete.id = 1;
        reactionToDelete.emojiId = 'smile';
        reactionToDelete.user = communicationUser1;
        reactionToDelete.post = post;
        post.reactions = [reactionToDelete];
        communicationService.setCourse(communicationCourse);
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function getEditButton(): DebugElement | null {
        return debugElement.query(By.css('[data-testid="posting-reaction-edit"]'));
    }

    function getDeleteButton(): DebugElement | null {
        return debugElement.query(By.css('jhi-confirm-icon'));
    }

    function getResolveButton(): DebugElement | null {
        return debugElement.query(By.css('#toggleElement'));
    }

    function getForwardButton(): DebugElement | null {
        return debugElement.query(By.css('[data-testid="posting-reaction-forward"]'));
    }

    it('should initialize user authority and reactions correctly', () => {
        communicationCourse.isAtLeastTutor = false;
        communicationService.setCourse(communicationCourse);
        const differentUser = { ...communicationUser1, id: 999 };
        vi.spyOn(communicationService, 'getUser').mockReturnValue(differentUser);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse()).toBe(false);
        fixture.changeDetectorRef.detectChanges();
        const reaction = getElement(debugElement, 'ngx-emoji');
        expect(reaction).toBeDefined();
        expect(component.reactionMetaDataMap()).toEqual({
            smile: {
                count: 1,
                hasReacted: false,
                reactingUsers: ['username1'],
            },
        });
    });

    it('should display edit and delete options to the author when not in read-only or preview mode', () => {
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('previewMode', false);
        fixture.componentRef.setInput('posting', { id: 1, title: 'Test Post' } as Post);
        vi.spyOn(communicationService, 'currentUserIsAuthorOfPosting').mockReturnValue(true);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(getDeleteButton()).not.toBeNull();
        expect(getEditButton()).not.toBeNull();
    });

    it('should display the delete option to user with channel moderation rights when not the author', () => {
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('previewMode', false);
        fixture.componentRef.setInput('isEmojiCount', false);
        fixture.componentRef.setInput('posting', { id: 1, title: 'Test Post' } as Post);

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        vi.spyOn(communicationService, 'currentUserIsAuthorOfPosting').mockReturnValue(false);
        vi.spyOn(communicationService, 'currentUserIsAtLeastInstructorInCourse').mockReturnValue(false);
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(getDeleteButton()).not.toBeNull();
    });
    it('should not display the edit option to user (even instructor) if they are not the author of posting with given conversation', () => {
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('previewMode', false);
        fixture.componentRef.setInput('isEmojiCount', false);

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        vi.spyOn(communicationService, 'currentUserIsAuthorOfPosting').mockReturnValue(false);
        vi.spyOn(communicationService, 'currentUserIsAtLeastInstructorInCourse').mockReturnValue(true);
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(getEditButton()).toBeNull();
    });

    it('should display the edit option to user if they are the author of posting', () => {
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('previewMode', false);
        fixture.componentRef.setInput('isEmojiCount', false);

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(getEditButton()).not.toBeNull();
    });

    it('should display the delete option to tutor if posting is in course-wide channel from a student', () => {
        communicationServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);
        fixture.componentRef.setInput('posting', { ...communicationResolvingAnswerPostUser1, post: { ...communicationPostInChannel }, authorRole: UserRole.USER } as AnswerPost);
        fixture.componentRef.setInput('isEmojiCount', false);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display edit and delete options to post author', () => {
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        fixture.changeDetectorRef.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display the edit option to user (even instructor) if they are not the author of posting', () => {
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(getEditButton()).toBeNull();
    });

    it('should not display edit and delete options when user is not the author and lacks permissions', () => {
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('previewMode', false);
        fixture.componentRef.setInput('posting', { conversation: { isCourseWide: false } } as Post);
        vi.spyOn(communicationService, 'currentUserIsAuthorOfPosting').mockReturnValue(false);
        vi.spyOn(communicationService, 'currentUserIsAtLeastInstructorInCourse').mockReturnValue(false);

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();

        expect(debugElement.query(By.css('fa-icon[icon="pencil-alt"]'))).toBeNull();
        expect(debugElement.query(By.directive(ConfirmIconComponent))).toBeNull();
    });

    it('should not display edit option but should display delete option to tutor if posting is in course-wide channel', () => {
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        communicationServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);
        fixture.componentRef.setInput('posting', { ...communicationPostInChannel, authorRole: UserRole.USER });
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is announcement', () => {
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        fixture.componentRef.setInput('posting', communicationAnnouncement);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should display edit and delete options to instructor if their posting is announcement', () => {
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        fixture.componentRef.setInput('posting', communicationAnnouncement);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display the delete option to instructor if posting is in course-wide channel from a student', () => {
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        communicationServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(channelConversation);
        fixture.componentRef.setInput('posting', { ...communicationPostInChannel, authorRole: UserRole.USER });

        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        component.setMayDelete();
        expect(getDeleteButton()).not.toBeNull();
    });

    it.each([
        { type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO,
        { type: ConversationType.GROUP_CHAT, creator: { id: 99 } },
        { type: ConversationType.ONE_TO_ONE },
    ])('should initialize user authority and reactions correctly with same user', (dto: ConversationDTO) => {
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(dto);
        accountService.userIdentity.set({ id: 99 } as User);

        reactionToDelete.user = { id: 99 } as User;
        post.reactions = [reactionToDelete];
        post.author!.id = 99;
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('isEmojiCount', true);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        expect(component.reactionMetaDataMap()).toEqual({
            smile: {
                count: 1,
                hasReacted: true,
                reactingUsers: [PLACEHOLDER_USER_REACTED],
            },
        });
        expect(component.pinTooltip()).toBe('artemisApp.communication.pinPostTooltip');
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

    it('should invoke communication service method with correctly built reaction to create it', () => {
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        const communicationServiceCreateReactionMock = vi.spyOn(communicationService, 'createReaction');
        reactionToCreate = new Reaction();
        reactionToCreate.emojiId = '+1';
        reactionToCreate.post = component.posting();
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(communicationServiceCreateReactionMock).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector()).toBeFalsy();
    });

    it('should invoke communication service method with own reaction to delete it', () => {
        post.author!.id = 99;
        fixture.componentRef.setInput('posting', post);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        const communicationServiceDeleteReactionMock = vi.spyOn(communicationService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(communicationServiceDeleteReactionMock).toHaveBeenCalledWith(reactionToDelete);
        expect(component.showReactionSelector()).toBeFalsy();
    });

    it('should invoke communication service method with own reaction to remove it', () => {
        component.ngOnInit();
        const addOrRemoveSpy = vi.spyOn(component, 'addOrRemoveReaction');
        component.updateReaction(reactionToDelete.emojiId!);
        expect(addOrRemoveSpy).toHaveBeenCalledWith(reactionToDelete.emojiId!);
    });

    it('should invoke communication service method when pin icon is toggled', () => {
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue({ type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin');
        pinEmoji.click();
        (component.posting() as Post)!.displayPriority = DisplayPriority.PINNED;
        expect(communicationServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting()!.id!, DisplayPriority.PINNED);
        // Trigger the effect by re-setting the posting input
        const updatedPost = { ...component.posting()! } as Post;
        fixture.componentRef.setInput('posting', updatedPost);
        fixture.detectChanges();
        // set correct tooltips for tutor and post that is pinned and not archived
        expect(component.pinTooltip()).toBe('artemisApp.communication.removePinPostTooltip');
    });

    it('should show non-clickable pin emoji with correct tooltip for student when post is pinned', () => {
        communicationCourse.isAtLeastTutor = false;
        communicationService.setCourse(communicationCourse);
        post.displayPriority = DisplayPriority.PINNED;
        fixture.componentRef.setInput('posting', post);
        component.ngOnInit();
        fixture.changeDetectorRef.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin.reaction-button--not-hoverable');
        expect(pinEmoji).toBeDefined();
        pinEmoji.click();
        expect(communicationServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is pinned
        expect(component.pinTooltip()).toBe('artemisApp.communication.pinnedPostTooltip');
    });

    it('should display button to show single answer', () => {
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('sortedAnswerPosts', [communicationPostExerciseUser1]);
        fixture.componentRef.setInput('showAnswers', false);
        fixture.changeDetectorRef.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn'));
        expect(answerNowButton).not.toBeNull();
        expect(component.sortedAnswerPosts()?.length).toBe(1);
    });

    it('should display button to show multiple answers', () => {
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('sortedAnswerPosts', [communicationPostExerciseUser1, communicationPostExerciseUser2]);
        fixture.componentRef.setInput('showAnswers', false);
        fixture.changeDetectorRef.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn'));
        expect(answerNowButton).not.toBeNull();
        expect(component.sortedAnswerPosts()?.length).toBe(2);
    });

    it('should display button to collapse answers', () => {
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('showAnswers', true);
        fixture.changeDetectorRef.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.collapse-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('collapseAnswers');
    });

    it('should emit showAnswersChange and openPostingCreateEditModal when openAnswerView is called', () => {
        const showAnswersChangeSpy = vi.spyOn(component.showAnswersChange, 'emit');
        const openPostingCreateEditModalSpy = vi.spyOn(component.openPostingCreateEditModal, 'emit');

        component.openAnswerView();

        expect(showAnswersChangeSpy).toHaveBeenCalledWith(true);
        expect(openPostingCreateEditModalSpy).toHaveBeenCalled();
    });

    it('should emit showAnswersChange and closePostingCreateEditModal when closeAnswerView is called', () => {
        const showAnswersChangeSpy = vi.spyOn(component.showAnswersChange, 'emit');
        const closePostingCreateEditModalSpy = vi.spyOn(component.closePostingCreateEditModal, 'emit');

        component.closeAnswerView();

        expect(showAnswersChangeSpy).toHaveBeenCalledWith(false);
        expect(closePostingCreateEditModalSpy).toHaveBeenCalled();
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        communicationServiceUserIsAtLeastTutorStub.mockReturnValue(false);
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        communicationServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        fixture.changeDetectorRef.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should emit event to create embedded view when edit icon is clicked', () => {
        fixture.componentRef.setInput('posting', communicationResolvingAnswerPostUser1);
        const openPostingCreateEditModalEmitSpy = vi.spyOn(component.openPostingCreateEditModal, 'emit');
        communicationServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        fixture.changeDetectorRef.detectChanges();
        getElement(debugElement, '[data-testid="posting-reaction-edit"]').click();
        expect(openPostingCreateEditModalEmitSpy).toHaveBeenCalledOnce();
    });

    it('answer now button should be invisible if answer is not the last one', () => {
        fixture.componentRef.setInput('posting', post);
        fixture.componentRef.setInput('isLastAnswer', false);
        fixture.changeDetectorRef.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn'));
        expect(answerNowButton).toBeNull();
    });

    it('should invoke communication service when toggle resolve is clicked', () => {
        unApprovedAnswerPost1.post = post;
        fixture.componentRef.setInput('posting', unApprovedAnswerPost1);
        fixture.componentRef.setInput('isEmojiCount', false);

        communicationServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        fixture.changeDetectorRef.detectChanges();
        expect(getResolveButton()).not.toBeNull();
        const previousState = (component.posting() as AnswerPost).resolvesPost;
        component.toggleResolvesPost();
        expect(component.getResolvesPost()).toEqual(!previousState);
        expect(communicationServiceUpdateAnswerPostMock).toHaveBeenCalledOnce();
    });

    it('should create a Reaction with answerPost when posting type is answerPost', () => {
        const answerPost = new AnswerPost();
        fixture.componentRef.setInput('posting', answerPost);

        const reaction = component.buildReaction('thumbsup');
        expect(reaction.answerPost).toBe(answerPost);
        expect(reaction.post).toBeUndefined();
    });

    it('should create a Reaction with post when posting type is post', () => {
        const post = new Post();
        fixture.componentRef.setInput('posting', post);

        const reaction = component.buildReaction('thumbsup');
        expect(reaction.post).toBe(post);
        expect(reaction.answerPost).toBeUndefined();
    });

    it('should not toggle pin when user has no permission', () => {
        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: false,
        } as ChannelDTO;
        component.setCanPin(channelConversation);
        fixture.changeDetectorRef.detectChanges();
        component.togglePin();
        expect(communicationServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
    });

    it('should emit isDeleteEvent when deletePosting is called', () => {
        const spy = vi.spyOn(component.isDeleteEvent, 'emit');
        component.deletePosting();
        expect(spy).toHaveBeenCalledWith(true);
    });

    it('should toggle pin and update displayPriority when user has permission', () => {
        vi.spyOn(communicationService, 'currentUserIsAtLeastTutorInCourse').mockReturnValue(true);

        const moderatorChannel = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        vi.spyOn(communicationService, 'getCurrentConversation').mockReturnValue(moderatorChannel);

        fixture.componentRef.setInput('posting', post);
        component.ngOnInit();
        expect(component.displayPriority()).toBe(DisplayPriority.NONE);

        component.togglePin();
        expect(communicationServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(post.id!, DisplayPriority.PINNED);
        expect(component.displayPriority()).toBe(DisplayPriority.PINNED);

        component.togglePin();
        expect(communicationServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(post.id!, DisplayPriority.NONE);
        expect(component.displayPriority()).toBe(DisplayPriority.NONE);
    });

    it('should display forward button and invoke forwardMessage function when clicked', () => {
        const forwardMessageSpy = vi.spyOn(component, 'forwardMessage');
        fixture.componentRef.setInput('isReadOnlyMode', false);
        fixture.componentRef.setInput('isEmojiCount', false);
        fixture.changeDetectorRef.detectChanges();
        const forwardButton = getForwardButton();

        expect(forwardButton).not.toBeNull();

        forwardButton?.nativeElement.click();
        fixture.changeDetectorRef.detectChanges();
        expect(forwardMessageSpy).toHaveBeenCalled();
    });

    it('should call openForwardMessageView with originalPostDetails when posting content is empty', () => {
        const openForwardMessageViewSpy = vi.spyOn(component, 'openForwardMessageView');
        const originalPost = { id: 42, content: 'Original content' } as Posting;

        fixture.componentRef.setInput('originalPostDetails', originalPost);
        fixture.componentRef.setInput('posting', { id: 1, content: '' } as Post);
        component.forwardMessage();

        expect(openForwardMessageViewSpy).toHaveBeenCalledOnce();
        expect(openForwardMessageViewSpy).toHaveBeenCalledWith(originalPost, false);
    });

    it('should call openForwardMessageView with posting when posting content is not empty', () => {
        const openForwardMessageViewSpy = vi.spyOn(component, 'openForwardMessageView');
        const postingWithContent = { id: 1, content: 'Non-empty content' } as Post;
        fixture.componentRef.setInput('posting', postingWithContent);
        component.forwardMessage();

        expect(openForwardMessageViewSpy).toHaveBeenCalledOnce();
        expect(openForwardMessageViewSpy).toHaveBeenCalledWith(postingWithContent, false);
    });

    it('should not call openForwardMessageView when course id is not set', async () => {
        communicationService.setCourse(undefined);

        const dialogServiceSpy = vi.spyOn(component['dialogService'], 'open').mockReturnValue({
            onClose: new Subject().asObservable(),
            close: vi.fn(),
        } as any);

        component.forwardMessage();
        await Promise.resolve();

        expect(dialogServiceSpy).not.toHaveBeenCalled();
        expect(createForwardedMessagesSpy).not.toHaveBeenCalled();
    });

    it('should call createForwardedMessages with the correct arguments', () => {
        const testPost = { id: 42 } as Posting;
        const testConversation = { id: 1337 } as Conversation;
        const content = 'Test content';
        const isAnswer = true;

        createForwardedMessagesSpy.mockReturnValue(of([]));

        component.forwardPost(testPost, testConversation, content, isAnswer);

        expect(createForwardedMessagesSpy).toHaveBeenCalledOnce();
        expect(createForwardedMessagesSpy).toHaveBeenCalledWith([testPost], testConversation, isAnswer, content);
        expect(consoleErrorSpy).not.toHaveBeenCalled();
    });

    it('should call markMessageAsUnread on communicationService', () => {
        const testPost = { id: 2, conversation: { id: 1 } } as Posting;
        fixture.componentRef.setInput('posting', testPost);
        const markMessageAsUnreadSpy = vi.spyOn(communicationService, 'markMessageAsUnread');

        component.markMessageAsUnread();

        expect(markMessageAsUnreadSpy).toHaveBeenCalledWith(testPost);
    });

    it('should handle empty content without logging errors', () => {
        const testPost = { id: 42 } as Posting;
        const testConversation = { id: 1337 } as Conversation;
        const emptyContent = '';
        const isAnswer = true;

        createForwardedMessagesSpy.mockReturnValue(of([]));
        component.forwardPost(testPost, testConversation, emptyContent, isAnswer);

        expect(createForwardedMessagesSpy).toHaveBeenCalledOnce();
        expect(createForwardedMessagesSpy).toHaveBeenCalledWith([testPost], testConversation, isAnswer, emptyContent);
        expect(consoleErrorSpy).not.toHaveBeenCalled();
    });

    it('should call createForwardedMessages with isAnswer set to false', () => {
        const testPost = { id: 42 } as Posting;
        const testConversation = { id: 1337 } as Conversation;
        const content = 'my content';
        const isAnswer = false;

        createForwardedMessagesSpy.mockReturnValue(of([]));
        component.forwardPost(testPost, testConversation, content, isAnswer);

        expect(createForwardedMessagesSpy).toHaveBeenCalledOnce();
        expect(createForwardedMessagesSpy).toHaveBeenCalledWith([testPost], testConversation, isAnswer, content);
        expect(consoleErrorSpy).not.toHaveBeenCalled();
    });

    it('should reload sidebar when forwardPost is completed', () => {
        const testPost = { id: 42 } as Posting;
        const testConversation = { id: 1337 } as Conversation;
        const content = 'Test content';
        const isAnswer = false;

        createForwardedMessagesSpy.mockReturnValue(of(null));

        component.forwardPost(testPost, testConversation, content, isAnswer);

        expect(reloadSidebarSpy).toHaveBeenCalledOnce();
    });
});

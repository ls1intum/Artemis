import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { DebugElement, input, runInInjectionContext } from '@angular/core';
import { Post } from 'app/communication/shared/entities/post.model';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { getElement } from 'test/helpers/utils/general-test.utils';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ReactionService } from 'app/communication/service/reaction.service';
import { MockReactionService } from 'test/helpers/mocks/service/mock-reaction.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DisplayPriority, UserRole } from 'app/communication/metis.util';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { By } from '@angular/platform-browser';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import {
    metisAnnouncement,
    metisCourse,
    metisPostExerciseUser1,
    metisPostInChannel,
    metisResolvingAnswerPostUser1,
    metisUser1,
    sortedAnswerArray,
    unApprovedAnswerPost1,
} from 'test/helpers/sample/metis-sample-data';
import { EmojiComponent } from 'app/communication/emoji/emoji.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { User } from 'app/core/user/user.model';
import { provideHttpClient } from '@angular/common/http';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { PostingReactionsBarComponent } from 'app/communication/posting-reactions-bar/posting-reactions-bar.component';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { of } from 'rxjs';
import { MockMetisConversationService } from 'test/helpers/mocks/service/mock-metis-conversation.service';

describe('PostingReactionsBarComponent', () => {
    let component: PostingReactionsBarComponent<Posting>;
    let fixture: ComponentFixture<PostingReactionsBarComponent<Posting>>;
    let debugElement: DebugElement;
    let metisService: MetisService;
    let accountService: AccountService;
    let metisServiceUpdateDisplayPriorityMock: jest.SpyInstance;
    let metisServiceUserIsAtLeastTutorStub: jest.SpyInstance;
    let metisServiceUserIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceUserIsAuthorOfPostingStub: jest.SpyInstance;
    let metisServiceUpdateAnswerPostMock: jest.SpyInstance;
    let post: Post;
    let reactionToCreate: Reaction;
    let reactionToDelete: Reaction;
    let consoleErrorSpy: jest.SpyInstance;
    let createForwardedMessagesSpy: jest.SpyInstance;

    const SPEECH_BALLOON_UNICODE = '1F4AC';
    const ARCHIVE_EMOJI_UNICODE = '1F4C2';
    const PIN_EMOJI_UNICODE = '1F4CC';
    const HEAVY_MULTIPLICATION_UNICODE = '2716';

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip)],
            declarations: [
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: MetisConversationService, useClass: MockMetisConversationService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostingReactionsBarComponent);
                metisService = TestBed.inject(MetisService);
                accountService = TestBed.inject(AccountService);
                debugElement = fixture.debugElement;
                component = fixture.componentInstance;
                metisServiceUpdateDisplayPriorityMock = jest.spyOn(metisService, 'updatePostDisplayPriority');
                metisServiceUserIsAtLeastTutorStub = jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse');
                metisServiceUserIsAtLeastInstructorStub = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceUserIsAuthorOfPostingStub = jest.spyOn(metisService, 'metisUserIsAuthorOfPosting');
                metisServiceUpdateAnswerPostMock = jest.spyOn(metisService, 'updateAnswerPost');
                consoleErrorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
                createForwardedMessagesSpy = jest.spyOn(metisService, 'createForwardedMessages');
                post = new Post();
                post.id = 1;
                post.author = metisUser1;
                post.displayPriority = DisplayPriority.NONE;
                runInInjectionContext(fixture.debugElement.injector, () => {
                    component.sortedAnswerPosts = input<AnswerPost[]>(sortedAnswerArray);
                    component.posting = input<Posting>(post);
                });
                reactionToDelete = new Reaction();
                reactionToDelete.id = 1;
                reactionToDelete.emojiId = 'smile';
                reactionToDelete.user = metisUser1;
                reactionToDelete.post = post;
                post.reactions = [reactionToDelete];
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

    function getResolveButton(): DebugElement | null {
        return debugElement.query(By.css('#toggleElement'));
    }

    function getForwardButton(): DebugElement | null {
        return debugElement.query(By.css('button.reaction-button.clickable.px-2.fs-small.forward'));
    }

    it('should initialize user authority and reactions correctly', () => {
        metisCourse.isAtLeastTutor = false;
        metisService.setCourse(metisCourse);
        component.ngOnInit();
        expect(component.isAtLeastTutorInCourse).toBeFalse();
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

    it('should display edit and delete options to the author when not in read-only or preview mode', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.previewMode = input<boolean>(false);
            component.posting = input<Posting>({ id: 1, title: 'Test Post' } as Post);
        });
        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(true);
        component.ngOnInit();
        fixture.detectChanges();

        expect(getDeleteButton()).not.toBeNull();
        expect(getEditButton()).not.toBeNull();
    });

    it('should display the delete option to user with channel moderation rights when not the author', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.previewMode = input<boolean>(false);
            component.isEmojiCount = input<boolean>(false);
            component.posting = input<Posting>({ id: 1, title: 'Test Post' } as Post);
        });

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(false);
        jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse').mockReturnValue(false);
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.detectChanges();

        expect(getDeleteButton()).not.toBeNull();
    });
    it('should not display the edit option to user (even instructor) if s/he is not the author of posting with given conversation', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.previewMode = input<boolean>(false);
            component.isEmojiCount = input<boolean>(false);
        });

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(false);
        jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse').mockReturnValue(true);
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.detectChanges();

        expect(getEditButton()).toBeNull();
    });

    it('should display the edit option to user if s/he is the author of posting', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.previewMode = input<boolean>(false);
            component.isEmojiCount = input<boolean>(false);
        });

        const channelConversation = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;

        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);

        component.ngOnInit();
        fixture.detectChanges();

        expect(getEditButton()).not.toBeNull();
    });

    it('should display the delete option to tutor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>({ ...metisResolvingAnswerPostUser1, post: { ...metisPostInChannel }, authorRole: UserRole.USER } as AnswerPost);
            component.isEmojiCount = input<boolean>(false);
        });
        component.ngOnInit();
        fixture.detectChanges();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display edit and delete options to post author', () => {
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display the edit option to user (even instructor) if s/he is not the author of posting', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);

        component.ngOnInit();
        fixture.detectChanges();

        expect(getEditButton()).toBeNull();
    });

    it('should not display edit and delete options when user is not the author and lacks permissions', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.previewMode = input<boolean>(false);
            component.posting = input<Posting>({ conversation: { isCourseWide: false } } as Post);
        });
        jest.spyOn(metisService, 'metisUserIsAuthorOfPosting').mockReturnValue(false);
        jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse').mockReturnValue(false);

        component.ngOnInit();
        fixture.detectChanges();

        expect(debugElement.query(By.css('fa-icon[icon="pencil-alt"]'))).toBeNull();
        expect(debugElement.query(By.directive(ConfirmIconComponent))).toBeNull();
    });

    it('should not display edit option but should display delete option to tutor if posting is in course-wide channel', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>({ ...metisPostInChannel, authorRole: UserRole.USER });
        });
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should not display edit and delete options to tutor if posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(metisAnnouncement);
        });
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should display edit and delete options to instructor if his posting is announcement', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(metisAnnouncement);
        });
        component.ngOnInit();
        fixture.detectChanges();
        expect(getEditButton()).not.toBeNull();
        expect(getDeleteButton()).not.toBeNull();
    });

    it('should display the delete option to instructor if posting is in course-wide channel from a student', () => {
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        const channelConversation = {
            type: ConversationType.CHANNEL,
            isCourseWide: true,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(channelConversation);
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>({ ...metisPostInChannel, authorRole: UserRole.USER });
        });

        component.ngOnInit();
        fixture.detectChanges();
        component.setMayDelete();
        expect(getDeleteButton()).not.toBeNull();
    });

    it.each([
        { type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO,
        { type: ConversationType.GROUP_CHAT, creator: { id: 99 } },
        { type: ConversationType.ONE_TO_ONE },
    ])('should initialize user authority and reactions correctly with same user', (dto: ConversationDTO) => {
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(dto);
        accountService.userIdentity.set({ id: 99 } as User);

        reactionToDelete.user = { id: 99 } as User;
        post.reactions = [reactionToDelete];
        runInInjectionContext(fixture.debugElement.injector, () => {
            post.author!.id = 99;
            component.posting = input<Posting>(post);
            component.isEmojiCount = input<boolean>(true);
        });
        component.ngOnInit();
        fixture.detectChanges();
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
        reactionToCreate.post = component.posting();
        component.addOrRemoveReaction(reactionToCreate.emojiId);
        expect(metisServiceCreateReactionMock).toHaveBeenCalledWith(reactionToCreate);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method with own reaction to delete it', () => {
        post.author!.id = 99;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
        });
        component.ngOnInit();
        fixture.detectChanges();
        const metisServiceDeleteReactionMock = jest.spyOn(metisService, 'deleteReaction');
        component.addOrRemoveReaction(reactionToDelete.emojiId!);
        expect(metisServiceDeleteReactionMock).toHaveBeenCalledWith(reactionToDelete);
        expect(component.showReactionSelector).toBeFalsy();
    });

    it('should invoke metis service method with own reaction to remove it', () => {
        component.ngOnInit();
        const addOrRemoveSpy = jest.spyOn(component, 'addOrRemoveReaction');
        component.updateReaction(reactionToDelete.emojiId!);
        expect(addOrRemoveSpy).toHaveBeenCalledWith(reactionToDelete.emojiId!);
    });

    it('should invoke metis service method when pin icon is toggled', () => {
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue({ type: ConversationType.CHANNEL, hasChannelModerationRights: true } as ChannelDTO);
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin');
        pinEmoji.click();
        (component.posting() as Post)!.displayPriority = DisplayPriority.PINNED;
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(component.posting()!.id!, DisplayPriority.PINNED);
        component.ngOnChanges();
        // set correct tooltips for tutor and post that is pinned and not archived
        expect(component.pinTooltip).toBe('artemisApp.metis.removePinPostTooltip');
    });

    it('should show non-clickable pin emoji with correct tooltip for student when post is pinned', () => {
        metisCourse.isAtLeastTutor = false;
        metisService.setCourse(metisCourse);
        post.displayPriority = DisplayPriority.PINNED;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
        });
        component.ngOnInit();
        fixture.detectChanges();
        const pinEmoji = getElement(debugElement, '.pin.reaction-button--not-hoverable');
        expect(pinEmoji).toBeDefined();
        pinEmoji.click();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
        // set correct tooltips for student and post that is pinned
        expect(component.pinTooltip).toBe('artemisApp.metis.pinnedPostTooltip');
    });

    it('should display button to show single answer', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
            component.sortedAnswerPosts = input<AnswerPost[]>([metisPostExerciseUser1]);
            component.showAnswers = input<boolean>(false);
        });
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('showSingleAnswer');
    });

    it('should display button to show multiple answers', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
            component.showAnswers = input<boolean>(false);
        });
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.expand-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('showMultipleAnswers');
    });

    it('should display button to collapse answers', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
            component.showAnswers = input<boolean>(true);
        });
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.collapse-answers-btn')).nativeElement;
        expect(answerNowButton.innerHTML).toContain('collapseAnswers');
    });

    it('should emit showAnswersChange and openPostingCreateEditModal when openAnswerView is called', () => {
        const showAnswersChangeSpy = jest.spyOn(component.showAnswersChange, 'emit');
        const openPostingCreateEditModalSpy = jest.spyOn(component.openPostingCreateEditModal, 'emit');

        component.openAnswerView();

        expect(showAnswersChangeSpy).toHaveBeenCalledWith(true);
        expect(openPostingCreateEditModalSpy).toHaveBeenCalled();
    });

    it('should emit showAnswersChange and closePostingCreateEditModal when closeAnswerView is called', () => {
        const showAnswersChangeSpy = jest.spyOn(component.showAnswersChange, 'emit');
        const closePostingCreateEditModalSpy = jest.spyOn(component.closePostingCreateEditModal, 'emit');

        component.closeAnswerView();

        expect(showAnswersChangeSpy).toHaveBeenCalledWith(false);
        expect(closePostingCreateEditModalSpy).toHaveBeenCalled();
    });

    it('should not display edit and delete options to users that are neither author or tutor', () => {
        metisServiceUserIsAtLeastTutorStub.mockReturnValue(false);
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(false);
        metisServiceUserIsAtLeastInstructorStub.mockReturnValue(false);
        fixture.detectChanges();
        expect(getEditButton()).toBeNull();
        expect(getDeleteButton()).toBeNull();
    });

    it('should emit event to create embedded view when edit icon is clicked', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(metisResolvingAnswerPostUser1);
        });
        const openPostingCreateEditModalEmitSpy = jest.spyOn(component.openPostingCreateEditModal, 'emit');
        metisServiceUserIsAuthorOfPostingStub.mockReturnValue(true);
        fixture.detectChanges();
        getElement(debugElement, '.edit').click();
        expect(openPostingCreateEditModalEmitSpy).toHaveBeenCalledOnce();
    });

    it('answer now button should be invisible if answer is not the last one', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
            component.isLastAnswer = input<boolean>(false);
        });
        fixture.detectChanges();
        const answerNowButton = fixture.debugElement.query(By.css('.reply-btn'));
        expect(answerNowButton).toBeNull();
    });

    it('should invoke metis service when toggle resolve is clicked', () => {
        runInInjectionContext(fixture.debugElement.injector, () => {
            unApprovedAnswerPost1.post = post;
            component.posting = input<Posting>(unApprovedAnswerPost1);
            component.isEmojiCount = input<boolean>(false);

            metisServiceUserIsAtLeastTutorStub.mockReturnValue(true);
            fixture.detectChanges();
            expect(getResolveButton()).not.toBeNull();
            const previousState = (component.posting() as AnswerPost).resolvesPost;
            component.toggleResolvesPost();
            expect(component.getResolvesPost()).toEqual(!previousState);
            expect(metisServiceUpdateAnswerPostMock).toHaveBeenCalledOnce();
        });
    });

    it('should create a Reaction with answerPost when posting type is answerPost', () => {
        const answerPost = new AnswerPost();
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(answerPost);
        });

        const reaction = component.buildReaction('thumbsup');
        expect(reaction.answerPost).toBe(answerPost);
        expect(reaction.post).toBeUndefined();
    });

    it('should create a Reaction with post when posting type is post', () => {
        const post = new Post();
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
        });

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
        fixture.detectChanges();
        component.togglePin();
        expect(metisServiceUpdateDisplayPriorityMock).not.toHaveBeenCalled();
    });

    it('should emit isDeleteEvent when deletePosting is called', () => {
        const spy = jest.spyOn(component.isDeleteEvent, 'emit');
        component.deletePosting();
        expect(spy).toHaveBeenCalledWith(true);
    });

    it('should toggle pin and update displayPriority when user has permission', () => {
        jest.spyOn(metisService, 'metisUserIsAtLeastTutorInCourse').mockReturnValue(true);

        const moderatorChannel = {
            type: ConversationType.CHANNEL,
            hasChannelModerationRights: true,
        } as ChannelDTO;
        jest.spyOn(metisService, 'getCurrentConversation').mockReturnValue(moderatorChannel);

        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(post);
        });
        component.ngOnInit();
        expect(component.displayPriority).toBe(DisplayPriority.NONE);

        component.togglePin();
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(post.id!, DisplayPriority.PINNED);
        expect(component.displayPriority).toBe(DisplayPriority.PINNED);

        component.togglePin();
        expect(metisServiceUpdateDisplayPriorityMock).toHaveBeenCalledWith(post.id!, DisplayPriority.NONE);
        expect(component.displayPriority).toBe(DisplayPriority.NONE);
    });

    it('should display forward button and invoke forwardMessage function when clicked', () => {
        const forwardMessageSpy = jest.spyOn(component, 'forwardMessage');
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.isReadOnlyMode = input<boolean>(false);
            component.isEmojiCount = input<boolean>(false);
        });
        fixture.detectChanges();
        const forwardButton = getForwardButton();

        expect(forwardButton).not.toBeNull();

        forwardButton?.nativeElement.click();
        fixture.detectChanges();
        expect(forwardMessageSpy).toHaveBeenCalled();
    });

    it('should call openForwardMessageView with originalPostDetails when posting content is empty', () => {
        const openForwardMessageViewSpy = jest.spyOn(component, 'openForwardMessageView');
        const originalPost = { id: 42, content: 'Original content' } as Posting;

        runInInjectionContext(fixture.debugElement.injector, () => {
            component.originalPostDetails = input<Posting>(originalPost);
            component.posting = input<Posting>({ id: 1, content: '' } as Post);
            component.forwardMessage();

            expect(openForwardMessageViewSpy).toHaveBeenCalledOnce();
            expect(openForwardMessageViewSpy).toHaveBeenCalledWith(originalPost, false);
        });
    });

    it('should call openForwardMessageView with posting when posting content is not empty', () => {
        const openForwardMessageViewSpy = jest.spyOn(component, 'openForwardMessageView');
        const postingWithContent = { id: 1, content: 'Non-empty content' } as Post;
        runInInjectionContext(fixture.debugElement.injector, () => {
            component.posting = input<Posting>(postingWithContent);
        });
        component.forwardMessage();

        expect(openForwardMessageViewSpy).toHaveBeenCalledOnce();
        expect(openForwardMessageViewSpy).toHaveBeenCalledWith(postingWithContent, false);
    });

    it('should not call openForwardMessageView when course id is not set', fakeAsync(() => {
        metisService.setCourse(undefined);

        const modalServiceSpy = jest.spyOn(component['modalService'], 'open').mockReturnValue({
            componentInstance: {
                users: { set: jest.fn() },
                channels: { set: jest.fn() },
                postToForward: { set: jest.fn() },
                courseId: { set: jest.fn() },
            },
            result: Promise.resolve({
                channels: [{ id: 1, type: ConversationType.CHANNEL } as Conversation],
                users: [],
                messageContent: 'Forwarded content to convo 1',
            }),
        } as any);

        component.forwardMessage();
        tick();

        expect(modalServiceSpy).not.toHaveBeenCalled();
        expect(createForwardedMessagesSpy).not.toHaveBeenCalled();
    }));

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
});

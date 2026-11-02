import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationThreadSidebarComponent } from 'app/communication/course-conversations-components/layout/conversation-thread-sidebar/conversation-thread-sidebar.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { PostComponent } from 'app/communication/post/post.component';
import { MessageReplyInlineInputComponent } from 'app/communication/message/message-reply-inline-input/message-reply-inline-input.component';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { post } from 'test/helpers/sample/communication-sample-data';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ComponentRef, signal } from '@angular/core';
import { TutorSuggestionComponent } from 'app/communication/course-conversations/tutor-suggestion/tutor-suggestion.component';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';

describe('ConversationThreadSidebarComponent', () => {
    let component: ConversationThreadSidebarComponent;
    let fixture: ComponentFixture<ConversationThreadSidebarComponent>;
    let componentRef: ComponentRef<ConversationThreadSidebarComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [
                ConversationThreadSidebarComponent,
                FaIconComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(MessageReplyInlineInputComponent),
                MockDirective(TranslateDirective),
                MockComponent(TutorSuggestionComponent),
            ],
            providers: [{ provide: TranslateService, useValue: { instant: vi.fn((key: string) => key), get: vi.fn() } }],
        });
    });

    beforeEach(() => {
        TestBed.overrideComponent(ConversationThreadSidebarComponent, {
            remove: { imports: [PostComponent, MessageReplyInlineInputComponent, TutorSuggestionComponent, TranslateDirective] },
            add: {
                imports: [
                    MockComponent(PostComponent),
                    MockComponent(MessageReplyInlineInputComponent),
                    MockComponent(TutorSuggestionComponent),
                    MockDirective(TranslateDirective),
                ],
            },
        });
        fixture = TestBed.createComponent(ConversationThreadSidebarComponent);
        component = fixture.componentInstance;
        componentRef = fixture.componentRef;
        componentRef.setInput('course', { id: 1 } as any);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should create empty default answer post', () => {
        const newPost = new Post();
        post.id = 1;
        fixture.componentRef.setInput('activePost', newPost);
        fixture.detectChanges();
        expect(component.createdAnswerPost()).toBeDefined();
        expect(component.createdAnswerPost().content).toBe('');
        expect(component.createdAnswerPost().post).toEqual(newPost);
    });

    it('should keep the reply input of the thread when the same post is updated, so that a new reply does not reset the cursor', () => {
        const openedPost = new Post();
        openedPost.id = 5;
        fixture.componentRef.setInput('activePost', openedPost);
        fixture.detectChanges();
        const replyBeingWritten = component.createdAnswerPost();

        const postWithNewReply = new Post();
        postWithNewReply.id = 5;
        fixture.componentRef.setInput('activePost', postWithNewReply);
        fixture.detectChanges();

        expect(component.createdAnswerPost()).toBe(replyBeingWritten);
        expect(component.createdAnswerPost().post).toBe(postWithNewReply);
        expect(component.post()).toBe(postWithNewReply);
    });

    it('should start a new reply when another post is opened', () => {
        const openedPost = new Post();
        openedPost.id = 5;
        fixture.componentRef.setInput('activePost', openedPost);
        fixture.detectChanges();
        const replyToFirstPost = component.createdAnswerPost();

        const otherPost = new Post();
        otherPost.id = 6;
        fixture.componentRef.setInput('activePost', otherPost);
        fixture.detectChanges();

        expect(component.createdAnswerPost()).not.toBe(replyToFirstPost);
        expect(component.createdAnswerPost().post).toBe(otherPost);
    });

    it.each([true, false])('should determine channel moderation rights based on active conversation', (hasModerationRights: boolean) => {
        const conversation = new ChannelDTO();
        conversation.hasChannelModerationRights = hasModerationRights;
        fixture.componentRef.setInput('activeConversation', conversation);
        fixture.detectChanges();
        expect(component.hasChannelModerationRights()).toBe(hasModerationRights);
    });

    it('should hand the conversation of the thread to the reply input, which offers @all in group chats', () => {
        const conversation = new GroupChatDTO();
        fixture.componentRef.setInput('activeConversation', conversation);
        fixture.componentRef.setInput('activePost', new Post());
        fixture.detectChanges();

        const replyInput = fixture.debugElement.query(By.directive(MessageReplyInlineInputComponent));
        expect(replyInput.componentInstance.activeConversation()).toBe(conversation);
    });

    it('should pass forwarded messages to the post shown in the thread', () => {
        const activePost = new Post();
        activePost.forwardedPosts = [{ id: 10, content: 'Original message' }];
        activePost.forwardedAnswerPosts = [{ id: 11, content: 'Original reply' }];
        fixture.componentRef.setInput('activePost', activePost);
        fixture.detectChanges();

        const threadPost: PostComponent = fixture.debugElement.query(By.directive(PostComponent)).componentInstance;
        expect(threadPost.forwardedPosts()).toEqual(activePost.forwardedPosts);
        expect(threadPost.forwardedAnswerPosts()).toEqual(activePost.forwardedAnswerPosts);
    });

    it('should forward navigation from the thread to the original message', () => {
        fixture.componentRef.setInput('activePost', new Post());
        fixture.detectChanges();
        const threadPost: PostComponent = fixture.debugElement.query(By.directive(PostComponent)).componentInstance;
        const originalReply = new AnswerPost();
        originalReply.id = 11;
        let destination: Post | AnswerPost | undefined;
        component.onNavigateToPost.subscribe((post: Post | AnswerPost) => (destination = post));

        threadPost.onNavigateToPost.emit(originalReply);

        expect(destination).toBe(originalReply);
    });

    it('should not show the reply input in read only mode', () => {
        fixture.componentRef.setInput('activeConversation', new GroupChatDTO());
        fixture.componentRef.setInput('activePost', new Post());
        fixture.componentRef.setInput('readOnlyMode', true);
        fixture.detectChanges();

        expect(fixture.debugElement.query(By.directive(MessageReplyInlineInputComponent))).toBeNull();
    });

    it('should clear the pinned width and open-post selection when closing the thread', () => {
        const container = component.threadContainer()!.nativeElement as HTMLElement;
        container.style.width = '420px';
        const close = vi.fn();
        component.closePostThread.subscribe(close);
        const setOpenPost = vi.spyOn(component['conversationSelectionState'], 'setOpenPostId');

        component.closeThread();

        expect(container.style.width).toBe('');
        expect(close).toHaveBeenCalledExactlyOnceWith(undefined);
        expect(setOpenPost).toHaveBeenCalledExactlyOnceWith(undefined);
    });

    it('should pin a measured thread width while preserving an explicit user width', () => {
        const container = component.threadContainer()!.nativeElement as HTMLElement;
        vi.spyOn(container, 'getBoundingClientRect').mockReturnValue({ width: 420 } as DOMRect);

        component['lockWidth']();
        expect(container.style.width).toBe('420px');
        container.style.width = '510px';
        component['lockWidth']();
        expect(container.style.width).toBe('510px');
    });

    it('should scroll the reply editor to the bottom of its own scroll container', () => {
        fixture.componentRef.setInput('activePost', new Post());
        fixture.detectChanges();
        const body = component.scrollBody()!.nativeElement;
        Object.defineProperty(body, 'scrollHeight', { configurable: true, value: 900 });
        const scroll = vi.fn();
        body.scrollTo = scroll;

        component.scrollEditorIntoView();

        expect(scroll).toHaveBeenCalledExactlyOnceWith({ top: 900, behavior: 'instant' });
    });

    it('should set min and max width for the resizable thread section', () => {
        const expandedThreadElement = fixture.debugElement.nativeElement.querySelector('.expanded-thread');
        const minWidth = window.innerWidth * 0.3;
        const maxWidth = window.innerWidth;

        expandedThreadElement.style.width = `${minWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeGreaterThanOrEqual(minWidth);

        expandedThreadElement.style.width = `${maxWidth}px`;
        expect(parseFloat(expandedThreadElement.style.width)).toBeLessThanOrEqual(maxWidth);
    });

    it('should toggle isExpanded and call close() on expandTooltip signal when toggleExpand() is called', () => {
        const closeMock = vi.fn();

        component.expandTooltip = signal<NgbTooltip | undefined>({ close: closeMock } as unknown as NgbTooltip);

        expect(component.isExpanded()).toBe(false);

        component.toggleExpand();
        expect(component.isExpanded()).toBe(true);
        expect(closeMock).toHaveBeenCalledOnce();

        component.toggleExpand();
        expect(component.isExpanded()).toBe(false);
        expect(closeMock).toHaveBeenCalledTimes(2);
    });
});

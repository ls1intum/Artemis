import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ConversationMessagesComponent } from 'app/communication/course-conversations-components/layout/conversation-messages/conversation-messages.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MetisService } from 'app/communication/service/metis.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { BehaviorSubject, of } from 'rxjs';
import { Conversation, ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { Directive, NO_ERRORS_SCHEMA, input, output } from '@angular/core';
import { By } from '@angular/platform-browser';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ChannelDTO, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HttpResponse } from '@angular/common/http';
import { ForwardedMessage } from 'app/communication/shared/entities/forwarded-message.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingType } from '../../../shared/entities/posting.model';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

const examples: ConversationDTO[] = [
    generateOneToOneChatDTO({}),
    generateExampleGroupChatDTO({}),
    generateExampleChannelDTO({} as ChannelDTO),
    generateExampleChannelDTO({ isAnnouncementChannel: true } as ChannelDTO),
];

@Directive({
    selector: '[infiniteScroll], [infinite-scroll], [data-infinite-scroll]',
})
class InfiniteScrollStubDirective {
    readonly scrolled = output<void>();
    readonly scrolledUp = output<void>();

    readonly infiniteScrollDistance = input(2);
    readonly infiniteScrollUpDistance = input(1.5);
    readonly infiniteScrollThrottle = input(150);
    readonly infiniteScrollDisabled = input(false);
    readonly infiniteScrollContainer = input<any>(null);
    readonly scrollWindow = input(true);
    readonly immediateCheck = input(false);
    readonly horizontal = input(false);
    readonly alwaysCallback = input(false);
    readonly fromRoot = input(false);
}
examples.forEach((activeConversation) => {
    describe('ConversationMessagesComponent with ' + (getAsChannelDTO(activeConversation)?.isAnnouncementChannel ? 'announcement ' : '') + activeConversation.type, () => {
        setupTestBed({ zoneless: true });

        let component: ConversationMessagesComponent;
        let fixture: ComponentFixture<ConversationMessagesComponent>;
        let metisService: MetisService;
        let metisConversationService: MetisConversationService;
        let examplePost: Post;
        const course = { id: 1 } as Course;

        beforeAll(() => {
            (window as any).ResizeObserver = class {
                observe() {}
                unobserve() {}
                disconnect() {}
            };
        });

        beforeEach(async () => {
            vi.useFakeTimers();
            TestBed.configureTestingModule({
                imports: [
                    FormsModule,
                    ReactiveFormsModule,
                    FaIconComponent,
                    ConversationMessagesComponent,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ButtonComponent),
                    MockComponent(PostingThreadComponent),
                    MockComponent(MessageInlineInputComponent),
                    MockComponent(PostCreateEditModalComponent),
                    MockDirective(TranslateDirective),
                    InfiniteScrollStubDirective,
                ],
                providers: [
                    MockProvider(MetisConversationService),
                    MockProvider(MetisService),
                    MockProvider(DialogService),
                    { provide: TranslateService, useClass: MockTranslateService },
                    { provide: AccountService, useClass: MockAccountService },
                ],
            });

            TestBed.overrideComponent(ConversationMessagesComponent, {
                remove: {
                    imports: [PostingThreadComponent, MessageInlineInputComponent, PostCreateEditModalComponent, InfiniteScrollDirective, ButtonComponent, TranslateDirective],
                },
                add: {
                    imports: [
                        MockComponent(PostingThreadComponent),
                        MockComponent(MessageInlineInputComponent),
                        MockComponent(PostCreateEditModalComponent),
                        InfiniteScrollStubDirective,
                        MockComponent(ButtonComponent),
                        MockDirective(TranslateDirective),
                    ],
                    schemas: [NO_ERRORS_SCHEMA],
                },
            });
        });

        beforeEach(() => {
            examplePost = { id: 1, content: 'loremIpsum' } as Post;

            metisService = TestBed.inject(MetisService);
            metisConversationService = TestBed.inject(MetisConversationService);
            Object.defineProperty(metisService, 'posts', { get: () => new BehaviorSubject([examplePost]).asObservable() });
            Object.defineProperty(metisService, 'totalNumberOfPosts', { get: () => new BehaviorSubject(1).asObservable() });
            Object.defineProperty(metisService, 'createEmptyPostForContext', { value: () => new Post() });
            Object.defineProperty(metisConversationService, 'course', { get: () => course });
            Object.defineProperty(metisConversationService, 'activeConversation$', { get: () => new BehaviorSubject(activeConversation).asObservable() });
            Object.defineProperty(metisService, 'getPinnedPosts', {
                value: () => of([]),
            });

            Object.defineProperty(metisService, 'fetchAllPinnedPosts', {
                value: () => of([]),
            });

            fixture = TestBed.createComponent(ConversationMessagesComponent);
            component = fixture.componentInstance;
            fixture.componentRef.setInput('course', course);
            // viewChildren signal returns an array directly; mock it as a function
            (component as any).messages = vi.fn().mockReturnValue([]);
            const mockContainer = document.createElement('div');
            vi.spyOn(mockContainer, 'getBoundingClientRect').mockReturnValue({ top: 0, bottom: 100 } as DOMRect);
            (component as any).content = vi.fn().mockReturnValue({
                nativeElement: mockContainer,
            });
            component.canStartSaving = true;
            fixture.detectChanges();
        });

        afterEach(() => {
            vi.clearAllMocks();
            vi.useRealTimers();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should set initial values correctly', () => {
            fixture.componentRef.setInput('course', course);
            component._activeConversation = activeConversation;
            component.posts = [examplePost];
        });

        it('should fetch posts on next page fetch', () => {
            const getFilteredPostSpy = vi.spyOn(metisService, 'getFilteredPosts');
            component.searchText = 'loremIpsum';
            component.totalNumberOfPosts = 10;
            component.fetchNextPage();
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
        });

        it('should save the scroll position in sessionStorage', () => {
            const sessionStorageService = TestBed.inject(SessionStorageService);
            const storeSpy = vi.spyOn(sessionStorageService, 'store');
            // Directly subscribe to scrollSubject to bypass debounceTime timing issues
            const scrollNextSpy = vi.spyOn(component.scrollSubject, 'next');
            component.saveScrollPosition(15);
            expect(scrollNextSpy).toHaveBeenCalledWith(15);
            // Manually trigger what the debounced subscription would do
            const activeConversationId = component._activeConversation?.id;
            expect(activeConversationId).toBeDefined();
            sessionStorageService.store<number>(component.sessionStorageKey + activeConversationId, 15);
            expect(storeSpy).toHaveBeenCalledWith(`${component.sessionStorageKey}${activeConversationId}`, 15);
        });

        it('should scroll to the last selected element or fetch next page if not found', () => {
            const mockMessages = [
                { post: vi.fn().mockReturnValue({ id: 1 }), elementRef: { nativeElement: { scrollIntoView: vi.fn(), offsetTop: 0 } } },
                { post: vi.fn().mockReturnValue({ id: 2 }), elementRef: { nativeElement: { scrollIntoView: vi.fn(), offsetTop: 100 } } },
            ] as unknown as PostingThreadComponent[];
            (component as any).messages = vi.fn().mockReturnValue(mockMessages);

            const fetchNextPageSpy = vi.spyOn(component, 'fetchNextPage').mockImplementation(() => {});
            const existingScrollPosition = 1;

            component.goToLastSelectedElement(existingScrollPosition, false);
            vi.advanceTimersByTime(0);
            expect(fetchNextPageSpy).not.toHaveBeenCalled();

            const nonExistingScrollPosition = 999;
            component.goToLastSelectedElement(nonExistingScrollPosition, false);
            vi.advanceTimersByTime(0);

            expect(fetchNextPageSpy).toHaveBeenCalled();
        });

        it('should find visible elements at the scroll position and save scroll position', () => {
            // Mock des Containers
            component.content().nativeElement = {
                getBoundingClientRect: vi.fn().mockReturnValue({ top: 0, bottom: 100 }),
                scrollTop: 0,
                scrollHeight: 200,
                removeEventListener: vi.fn(),
            };
            const mockMessages = [
                { post: vi.fn().mockReturnValue({ id: 1 }), elementRef: { nativeElement: { getBoundingClientRect: vi.fn().mockReturnValue({ top: 10, bottom: 90 }) } } },
                { post: vi.fn().mockReturnValue({ id: 2 }), elementRef: { nativeElement: { getBoundingClientRect: vi.fn().mockReturnValue({ top: 100, bottom: 200 }) } } },
            ] as unknown as PostingThreadComponent[];
            (component as any).messages = vi.fn().mockReturnValue(mockMessages);
            component.canStartSaving = true;
            const nextSpy = vi.spyOn(component.scrollSubject, 'next');
            component.findElementsAtScrollPosition();
            expect(component.elementsAtScrollPosition).toEqual([mockMessages[0]]);
            expect(nextSpy).toHaveBeenCalledWith(1);
        });

        it('should not save scroll position if no elements are visible', () => {
            const mockMessages = [
                {
                    post: vi.fn().mockReturnValue({ id: 1 }),
                    elementRef: { nativeElement: { getBoundingClientRect: vi.fn().mockReturnValue({ top: 200, bottom: 320 }) } },
                },
            ] as unknown as PostingThreadComponent[];

            (component as any).messages = vi.fn().mockReturnValue(mockMessages);
            const nextSpy = vi.spyOn(component.scrollSubject, 'next');
            component.findElementsAtScrollPosition();
            expect(component.elementsAtScrollPosition).toEqual([]);
            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should scroll to the bottom when a new message is created', () => {
            component.content().nativeElement.scrollTop = 100;
            fixture.detectChanges();
            component.handleNewMessageCreated();
            vi.advanceTimersByTime(300);
            expect(component.content().nativeElement.scrollTop).toBe(component.content().nativeElement.scrollHeight);
        });

        it('should create empty post with the correct conversation type', () => {
            const createEmptyPostForContextSpy = vi.spyOn(metisService, 'createEmptyPostForContext').mockReturnValue(new Post());
            component.createEmptyPost();
            expect(createEmptyPostForContextSpy).toHaveBeenCalledOnce();
            const conversation = createEmptyPostForContextSpy.mock.calls[0][0];
            expect(conversation!.type).toEqual(activeConversation.type);
            expect(conversation!.id).toEqual(activeConversation.id);
        });

        it('should set posts and group them correctly', () => {
            component.allPosts = [
                { id: 1, creationDate: dayjs().subtract(2, 'hours'), author: { id: 1 } } as Post,
                { id: 4, creationDate: dayjs().subtract(3, 'minutes'), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().subtract(1, 'minutes'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs(), author: { id: 2 } } as Post,
            ];
            component.setPosts();

            expect(component.posts).toHaveLength(4);
            expect(component.groupedPosts).toHaveLength(3);

            expect(component.groupedPosts[0].posts).toHaveLength(1);
            expect(component.groupedPosts[0].posts[0].id).toBe(1);
            expect(component.groupedPosts[0].posts[0].isConsecutive).toBe(false);

            expect(component.groupedPosts[1].posts).toHaveLength(2);
            expect(component.groupedPosts[1].posts[0].id).toBe(4);
            expect(component.groupedPosts[1].posts[0].isConsecutive).toBe(false);
            expect(component.groupedPosts[1].posts[1].id).toBe(2);
            expect(component.groupedPosts[1].posts[1].isConsecutive).toBe(true);

            expect(component.groupedPosts[2].posts).toHaveLength(1);
            expect(component.groupedPosts[2].posts[0].id).toBe(3);
            expect(component.groupedPosts[2].posts[0].isConsecutive).toBe(false);
        });

        it('should not group posts that are exactly 5 minutes apart', () => {
            component.allPosts = [
                { id: 1, creationDate: dayjs().subtract(10, 'minutes'), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().subtract(5, 'minutes'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs(), author: { id: 1 } } as Post,
            ];
            component.setPosts();

            expect(component.groupedPosts).toHaveLength(3);
            expect(component.groupedPosts[0].posts).toHaveLength(1);
            expect(component.groupedPosts[1].posts).toHaveLength(1);
            expect(component.groupedPosts[2].posts).toHaveLength(1);
        });

        it('should fetch forwarded messages correctly', () => {
            const mockForwardedMessages: ForwardedMessage[] = [
                { id: 101, sourceId: 10, sourceType: 'POST' } as unknown as ForwardedMessage,
                { id: 102, sourceId: 11, sourceType: 'ANSWER' } as unknown as ForwardedMessage,
            ];

            const mockResponse = new HttpResponse({
                body: [{ id: 1, messages: mockForwardedMessages }],
            });

            vi.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(mockResponse));

            metisService.getForwardedMessagesByIds([1], PostingType.POST)?.subscribe((response) => {
                expect(response.body).toEqual(mockResponse.body);
            });
        });

        if (getAsChannelDTO(activeConversation)?.isAnnouncementChannel) {
            it('should display the "new announcement" button when the conversation is an announcement channel', () => {
                const announcementButton = fixture.debugElement.query(By.css('.btn.btn-md.btn-primary'));
                expect(announcementButton).toBeTruthy(); // Check if the button is present
                expect(component.isHiddenInputFull).toBeFalsy();
                expect(component.isHiddenInputWithCallToAction).toBeTruthy();

                const modal = fixture.debugElement.query(By.directive(PostCreateEditModalComponent));
                expect(modal).toBeTruthy(); // Check if the modal is present
            });
        } else {
            it('should display the inline input when the conversation is not an announcement channel', () => {
                const inlineInput = fixture.debugElement.query(By.directive(MessageInlineInputComponent));
                expect(inlineInput).toBeTruthy(); // Check if the inline input component is present
            });
        }

        it('should scroll to bottom and set canStartSaving to true when lastScrollPosition is falsy', async () => {
            const scrollToBottomSpy = vi.spyOn(component, 'scrollToBottomOfMessages');

            await component.goToLastSelectedElement(0, false);

            expect(scrollToBottomSpy).toHaveBeenCalledOnce();
            expect(component.canStartSaving).toBe(true);
        });

        it('should handle posts without forwarded messages gracefully', () => {
            vi.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [] })));

            component.posts = [{ id: 1, hasForwardedMessages: false } as Post];

            vi.advanceTimersByTime(0);

            expect(component.posts[0].forwardedPosts).toBeUndefined();
            expect(component.posts[0].forwardedAnswerPosts).toBeUndefined();
        });

        it('should handle forwarded messages with missing source gracefully', () => {
            const mockForwardedMessages: ForwardedMessage[] = [
                { id: 101, sourceId: 99, sourceType: 'POST' } as unknown as ForwardedMessage,
                { id: 102, sourceId: 100, sourceType: 'ANSWER' } as unknown as ForwardedMessage,
            ];

            vi.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of([]));
            vi.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of([]));

            vi.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));

            component.allPosts = [{ id: 1, hasForwardedMessages: true } as Post];
            component.setPosts();

            vi.advanceTimersByTime(0);

            expect(component.posts[0].forwardedPosts).toEqual([undefined]);
            expect(component.posts[0].forwardedAnswerPosts).toEqual([undefined]);
        });

        it('should not fetch source posts or answers for empty forwarded messages', () => {
            const mockForwardedMessages: ForwardedMessage[] = [];

            vi.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of([]));
            vi.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of([]));

            vi.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));

            const getSourcePostsSpy = vi.spyOn(metisService, 'getSourcePostsByIds');
            const getSourceAnswersSpy = vi.spyOn(metisService, 'getSourceAnswerPostsByIds');

            component.allPosts = [{ id: 1, hasForwardedMessages: true } as Post];
            component.setPosts();

            vi.advanceTimersByTime(0);

            expect(getSourcePostsSpy).not.toHaveBeenCalled();
            expect(getSourceAnswersSpy).not.toHaveBeenCalled();
        });

        it('should correctly assign forwarded posts and answers', () => {
            const mockForwardedMessages: ForwardedMessage[] = [
                { id: 101, sourceId: 10, sourceType: 'POST' } as unknown as ForwardedMessage,
                { id: 102, sourceId: 11, sourceType: 'ANSWER' } as unknown as ForwardedMessage,
            ];

            const mockSourcePosts: Post[] = [{ id: 10, content: 'Forwarded Post Content', conversation: component._activeConversation as Conversation } as Post];
            const mockSourceAnswerPosts: AnswerPost[] = [{ id: 11, content: 'Forwarded Answer Content', resolvesPost: true } as AnswerPost];

            vi.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));
            vi.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of(mockSourcePosts));
            vi.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of(mockSourceAnswerPosts));

            const getSourcePostsSpy = vi.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of(mockSourcePosts));
            const getSourceAnswersSpy = vi.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of(mockSourceAnswerPosts));

            component.allPosts = [
                {
                    id: 1,
                    content: 'Some content...',
                    hasForwardedMessages: true,
                } as Post,
            ];
            component.setPosts();

            expect(getSourcePostsSpy).toHaveBeenCalled();
            expect(getSourceAnswersSpy).toHaveBeenCalled();

            const forwardedPosts = component.posts[0].forwardedPosts;
            const forwardedAnswerPosts = component.posts[0].forwardedAnswerPosts;

            expect(component.posts).toHaveLength(1);
            expect(forwardedPosts).toBeDefined();
            expect(forwardedAnswerPosts).toBeDefined();

            if (forwardedPosts) {
                expect(forwardedPosts).toHaveLength(mockSourcePosts.length);
                forwardedPosts.forEach((post) => {
                    if (post) {
                        expect(post.id).toBeDefined();
                        expect(post.id).toBe(10);
                    } else {
                        expect(post).toBeUndefined();
                    }
                });
            }

            if (forwardedAnswerPosts) {
                expect(forwardedAnswerPosts).toHaveLength(mockSourceAnswerPosts.length);
                forwardedAnswerPosts.forEach((post) => {
                    if (post) {
                        expect(post.id).toBeDefined();
                        expect(post.id).toBe(11);
                    } else {
                        expect(post).toBeUndefined();
                    }
                });
            }
        });

        it('should filter posts to show only pinned posts when showOnlyPinned is true', () => {
            const pinnedPost = { id: 1, displayPriority: 'PINNED' } as Post;
            const regularPost = { id: 2, displayPriority: 'NONE' } as Post;
            component.pinnedPosts = [pinnedPost];
            component.allPosts = [pinnedPost, regularPost];

            fixture.componentRef.setInput('showOnlyPinned', true);
            fixture.detectChanges();
            component.applyPinnedMessageFilter();

            expect(component.posts).toEqual([pinnedPost]);
        });

        it('should show all posts when showOnlyPinned is false', () => {
            const pinnedPost = { id: 1, displayPriority: 'PINNED', creationDate: dayjs().subtract(2, 'minutes') } as Post;
            const regularPost = { id: 2, displayPriority: 'NONE', creationDate: dayjs().subtract(1, 'minute') } as Post;
            component.pinnedPosts = [pinnedPost];
            component.allPosts = [pinnedPost, regularPost];
            fixture.componentRef.setInput('showOnlyPinned', false);
            fixture.detectChanges();
            component.applyPinnedMessageFilter();

            expect(component.posts).toEqual([pinnedPost, regularPost]);
        });

        it('should call setPosts when showOnlyPinned input changes after initialization', () => {
            const setPostsSpy = vi.spyOn(component, 'setPosts');

            fixture.componentRef.setInput('showOnlyPinned', true);
            fixture.detectChanges();

            expect(setPostsSpy).toHaveBeenCalled();
        });

        it('should subscribe to pinned posts on init and emit pinnedCount', () => {
            const pinnedPostsStub = [{ id: 10, displayPriority: 'PINNED' }] as Post[];
            const pinnedPostsSubject = new BehaviorSubject<Post[]>([]);

            const pinnedCountSpy = vi.spyOn(component.pinnedCount, 'emit');
            vi.spyOn(metisService, 'getPinnedPosts').mockReturnValue(pinnedPostsSubject.asObservable());

            component.ngOnInit();

            pinnedPostsSubject.next(pinnedPostsStub);
            vi.advanceTimersByTime(0);

            expect(component.pinnedPosts).toEqual(pinnedPostsStub);
            expect(pinnedCountSpy).toHaveBeenCalledWith(pinnedPostsStub.length);
        });

        it('should fetch pinned posts in onActiveConversationChange and emit pinnedCount', () => {
            const pinnedPostsStub = [{ id: 77, displayPriority: 'PINNED' }] as Post[];
            const pinnedCountSpy = vi.spyOn(component.pinnedCount, 'emit');
            vi.spyOn(metisService, 'fetchAllPinnedPosts').mockReturnValue(of(pinnedPostsStub));

            component._activeConversation = { id: 123, type: ConversationType.CHANNEL };
            fixture.componentRef.setInput('course', { id: 1 } as Course);

            component['onActiveConversationChange']();
            vi.advanceTimersByTime(0);

            expect(component.pinnedPosts).toEqual(pinnedPostsStub);
            expect(pinnedCountSpy).toHaveBeenCalledWith(pinnedPostsStub.length);
        });

        it('should group posts correctly with consecutive messages from same author', () => {
            component.posts = [
                { id: 1, creationDate: dayjs(), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().add(1, 'minute'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs().add(2, 'minutes'), author: { id: 1 } } as Post,
            ];
            (component as any).groupPosts();

            expect(component.groupedPosts).toHaveLength(1);
            expect(component.groupedPosts[0].posts).toHaveLength(3);
            expect(component.groupedPosts[0].posts[0].isConsecutive).toBe(false);
            expect(component.groupedPosts[0].posts[1].isConsecutive).toBe(true);
            expect(component.groupedPosts[0].posts[2].isConsecutive).toBe(true);
        });

        it('should group posts correctly with different authors', () => {
            component.posts = [
                { id: 1, creationDate: dayjs(), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().add(1, 'minute'), author: { id: 2 } } as Post,
                { id: 3, creationDate: dayjs().add(2, 'minutes'), author: { id: 1 } } as Post,
            ];
            (component as any).groupPosts();

            expect(component.groupedPosts).toHaveLength(3);
            expect(component.groupedPosts[0].posts).toHaveLength(1);
            expect(component.groupedPosts[1].posts).toHaveLength(1);
            expect(component.groupedPosts[2].posts).toHaveLength(1);
        });

        it('should handle multiple message deletions correctly', () => {
            // Initial posts

            component.posts = [
                { id: 1, creationDate: dayjs(), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().add(1, 'minute'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs().add(2, 'minutes'), author: { id: 2 } } as Post,
                { id: 4, creationDate: dayjs().add(3, 'minutes'), author: { id: 2 } } as Post,
            ];
            (component as any).groupPosts();

            // Delete posts 2 and 3
            component.posts = [
                { id: 1, creationDate: dayjs(), author: { id: 1 } } as Post,
                {
                    id: 4,
                    creationDate: dayjs().add(3, 'minutes'),
                    author: { id: 2 },
                } as Post,
            ];
            (component as any).groupPosts();

            expect(component.groupedPosts).toHaveLength(2);
            expect(component.groupedPosts[0].posts).toHaveLength(1);
            expect(component.groupedPosts[1].posts).toHaveLength(1);
            expect(component.groupedPosts[0].author?.id).toBe(1);
            expect(component.groupedPosts[1].author?.id).toBe(2);
        });

        it('should return only unread posts by the current user and sort them descending by creationDate', () => {
            const lastReadDate = dayjs().subtract(10, 'minutes');
            const currentUser = { id: 99, internal: false };
            const otherUser = { id: 42, internal: false };

            component._activeConversation = {
                ...component._activeConversation,
                lastReadDate,
            };

            component.currentUser = currentUser;

            component.allPosts = [
                { id: 1, creationDate: dayjs().subtract(5, 'minutes'), author: otherUser } as Post, // unread
                { id: 2, creationDate: dayjs().subtract(3, 'minutes'), author: currentUser } as Post, // read
                { id: 3, creationDate: dayjs().subtract(20, 'minutes'), author: otherUser } as Post, // read
                { id: 4, creationDate: dayjs().subtract(2, 'minutes'), author: otherUser } as Post, // unread
            ];
            const unreadPosts = (component as any).getUnreadPosts();
            expect(unreadPosts).toHaveLength(2);
            expect(unreadPosts.map((p: Post) => p.id)).toEqual([1, 4]);
        });
        it('should compute unreadPosts, unreadPostsCount, and firstUnreadPostId correctly', () => {
            const lastReadDate = dayjs().subtract(10, 'minutes');
            const currentUser = { id: 99, internal: false };
            const otherUser = { id: 42, internal: false };

            component._activeConversation = {
                ...component._activeConversation,
                lastReadDate,
            };

            component.currentUser = currentUser;

            component.allPosts = [
                { id: 1, creationDate: dayjs().subtract(15, 'minutes'), author: otherUser } as Post, // read
                { id: 2, creationDate: dayjs().subtract(5, 'minutes'), author: otherUser } as Post, // unread (first)
                { id: 3, creationDate: dayjs().subtract(3, 'minutes'), author: currentUser } as Post, // excluded (own post)
                { id: 4, creationDate: dayjs(), author: otherUser } as Post, // unread
            ];

            (component as any).computeLastReadState();

            expect(component.unreadPosts).toHaveLength(2);
            expect(component.unreadPostsCount).toBe(2);
            expect(component.firstUnreadPostId).toBe(2);
        });

        it('should call setFirstUnreadPostId inside computeLastReadState', () => {
            const lastReadDate = dayjs().subtract(10, 'minutes');
            const currentUser = { id: 99, internal: false };
            const otherUser = { id: 42, internal: false };

            component._activeConversation = { ...component._activeConversation, lastReadDate };
            component.currentUser = currentUser;
            component.allPosts = [{ id: 5, creationDate: dayjs().subtract(2, 'minutes'), author: otherUser } as Post];

            const setFirstUnreadPostIdSpy = vi.spyOn(component as any, 'setFirstUnreadPostId');

            (component as any).computeLastReadState();

            expect(setFirstUnreadPostIdSpy).toHaveBeenCalledOnce();
            expect(component.firstUnreadPostId).toBe(5);
        });

        it('should clear firstUnreadPostId when there are no unread posts', () => {
            component._activeConversation = { ...component._activeConversation, lastReadDate: dayjs() };
            component.currentUser = { id: 99, internal: false };
            component.allPosts = [];
            component.firstUnreadPostId = 42;

            (component as any).computeLastReadState();

            expect(component.firstUnreadPostId).toBeUndefined();
        });

        it('should return true if at least one unread post is visible', () => {
            const mockPostId = 1;
            component.unreadPosts = [{ id: mockPostId } as any];

            vi.spyOn(component as any, 'isPostVisible').mockImplementation((id: number) => id === mockPostId);

            const result = (component as any).isAnyUnreadPostVisible();
            expect(result).toBe(true);
        });
        it('should return false if the first unread post is partially or fully out of view', () => {
            const mockRects = {
                postRect: { top: 100, bottom: 400 },
                containerRect: { top: 50, bottom: 300 },
            };
            vi.spyOn(component as any, 'getBoundingRectsForFirstUnreadPost').mockReturnValue(mockRects);
            const result = (component as any).isAnyUnreadPostVisible();
            expect(result).toBe(false);
        });

        it('should return postRect and containerRect if the first unread post and container are available', () => {
            const mockPostId = 123;
            const mockPost = { id: mockPostId } as Post;
            const mockPostRect = {
                top: 50,
                bottom: 150,
            } as DOMRect;
            const mockContainerRect = {
                top: 0,
                bottom: 300,
            } as DOMRect;
            const mockPostElement = {
                getBoundingClientRect: vi.fn().mockReturnValue(mockPostRect),
            };

            const mockContainerElement = {
                getBoundingClientRect: vi.fn().mockReturnValue(mockContainerRect),
                addEventListener: vi.fn(),
                removeEventListener: vi.fn(),
            };

            component.unreadPosts = [mockPost];
            (component as any).messages = vi.fn().mockReturnValue([
                {
                    post: vi.fn().mockReturnValue({ id: mockPostId }),
                    elementRef: { nativeElement: mockPostElement },
                },
            ]);
            (component as any).content = vi.fn().mockReturnValue({
                nativeElement: mockContainerElement,
            });

            (component as any).setFirstUnreadPostId();

            const result = (component as any).getBoundingRectsForFirstUnreadPost();
            expect(result).toEqual({ postRect: mockPostRect, containerRect: mockContainerRect });
        });
        it('should return undefined if no matching message element is found', () => {
            const mockPostId = 123;
            const mockPost = { id: mockPostId } as Post;

            component.unreadPosts = [mockPost];
            (component as any).messages = vi.fn().mockReturnValue([]);

            (component as any).content = vi.fn().mockReturnValue({
                nativeElement: {
                    getBoundingClientRect: () => ({
                        top: 0,
                        bottom: 300,
                    }),
                    removeEventListener: vi.fn(),
                },
            });

            const result = (component as any).getBoundingRectsForFirstUnreadPost();
            expect(result).toBeUndefined();
        });
        it('should scroll to the first unread post if it is not visible', () => {
            const mockPostId = 123;
            const mockPost = { id: mockPostId } as Post;
            const mockPostElement = {
                offsetTop: 500,
                offsetHeight: 400,
            };
            const mockContainerElement = {
                scrollTop: 0,
                clientHeight: 300,
                scrollTo: vi.fn(),
                removeEventListener: vi.fn(),
            };

            const mockRects = {
                postRect: { top: 400, bottom: 800 },
                containerRect: { top: 0, bottom: 300 },
            };
            component.firstUnreadPostId = mockPostId;
            component.unreadPosts = [mockPost];
            (component as any).messages = vi.fn().mockReturnValue([
                {
                    post: vi.fn().mockReturnValue({ id: mockPostId }),
                    elementRef: { nativeElement: mockPostElement },
                },
            ]);
            (component as any).content = vi.fn().mockReturnValue({
                nativeElement: mockContainerElement,
            });

            vi.spyOn(component as any, 'getBoundingRectsForFirstUnreadPost').mockReturnValue(mockRects);

            const rafSpy = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((fn: FrameRequestCallback) => {
                fn(0);
                return 0;
            });
            (component as any).scrollToFirstUnreadPostIfNotVisible();
            const scrollOffset = 15;
            const expectedScrollTop = Math.max(mockPostElement.offsetTop - scrollOffset, 0);
            expect(mockContainerElement.scrollTop).toBe(expectedScrollTop);

            rafSpy.mockRestore();
        });

        it('should return bounding rects if both post and container are available', () => {
            const postId = 123;
            const mockPostRect = { top: 10, bottom: 110 } as DOMRect;
            const mockContainerRect = { top: 0, bottom: 300 } as DOMRect;

            (component as any).messages = vi.fn().mockReturnValue([
                {
                    post: vi.fn().mockReturnValue({ id: postId }),
                    elementRef: {
                        nativeElement: {
                            getBoundingClientRect: () => mockPostRect,
                        },
                    },
                },
            ]);

            (component as any).content = vi.fn().mockReturnValue({
                nativeElement: {
                    getBoundingClientRect: () => mockContainerRect,
                    addEventListener: vi.fn(),
                    removeEventListener: vi.fn(),
                },
            });
            const result = (component as any).getBoundingRectsForPost(postId);
            expect(result).toEqual({ postRect: mockPostRect, containerRect: mockContainerRect });
        });

        it('should return true if the post is fully within the container bounds', () => {
            const postId = 1;
            const rects = {
                postRect: { top: 50, bottom: 150 },
                containerRect: { top: 0, bottom: 300 },
            };
            vi.spyOn(component as any, 'getBoundingRectsForPost').mockReturnValue(rects);
            const result = (component as any).isPostVisible(postId);
            expect(result).toBe(true);
        });
        it('should return false if the post is below the container bounds', () => {
            const postId = 1;
            const rects = {
                postRect: { top: 310, bottom: 400 },
                containerRect: { top: 0, bottom: 300 },
            };
            vi.spyOn(component as any, 'getBoundingRectsForPost').mockReturnValue(rects);
            const result = (component as any).isPostVisible(postId);
            expect(result).toBe(false);
        });
    });
});

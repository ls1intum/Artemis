import { ConversationMessagesComponent } from 'app/overview/course-conversations/layout/conversation-messages/conversation-messages.component';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import { Post } from 'app/entities/metis/post.model';
import { BehaviorSubject, of } from 'rxjs';
import { Conversation, ConversationDTO, ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from '../../helpers/conversationExampleModels';
import { Directive, ElementRef, EventEmitter, input, Input, Output, QueryList, runInInjectionContext } from '@angular/core';
import { By } from '@angular/platform-browser';
import { Course } from 'app/entities/course.model';
import { ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HttpResponse } from '@angular/common/http';
import { ForwardedMessage } from 'app/entities/metis/forwarded-message.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingType } from '../../../../../../../../main/webapp/app/entities/metis/posting.model';

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
    @Output() scrolled = new EventEmitter<void>();
    @Output() scrolledUp = new EventEmitter<void>();

    @Input() infiniteScrollDistance = 2;
    @Input() infiniteScrollUpDistance = 1.5;
    @Input() infiniteScrollThrottle = 150;
    @Input() infiniteScrollDisabled = false;
    @Input() infiniteScrollContainer: any = null;
    @Input() scrollWindow = true;
    @Input() immediateCheck = false;
    @Input() horizontal = false;
    @Input() alwaysCallback = false;
    @Input() fromRoot = false;
}
examples.forEach((activeConversation) => {
    describe('ConversationMessagesComponent with ' + (getAsChannelDTO(activeConversation)?.isAnnouncementChannel ? 'announcement ' : '') + activeConversation.type, () => {
        let component: ConversationMessagesComponent;
        let fixture: ComponentFixture<ConversationMessagesComponent>;
        let metisService: MetisService;
        let metisConversationService: MetisConversationService;
        let examplePost: Post;
        const course = { id: 1 } as Course;

        beforeEach(waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [FormsModule, ReactiveFormsModule],
                declarations: [
                    ConversationMessagesComponent,
                    InfiniteScrollStubDirective,
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(ButtonComponent),
                    MockComponent(FaIconComponent),
                    MockComponent(PostingThreadComponent),
                    MockComponent(MessageInlineInputComponent),
                    MockComponent(PostCreateEditModalComponent),
                    MockDirective(TranslateDirective),
                ],
                providers: [MockProvider(MetisConversationService), MockProvider(MetisService), MockProvider(NgbModal)],
            }).compileComponents();
        }));

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
            component.course = course;
            component.messages = {
                toArray: jest.fn().mockReturnValue([]),
            } as any;
            component.content = {
                nativeElement: {
                    getBoundingClientRect: jest.fn().mockReturnValue({ top: 0, bottom: 100 }),
                },
            } as any;
            component.canStartSaving = true;
            fixture.detectChanges();
        });

        afterEach(() => {
            jest.clearAllMocks();
        });

        it('should create', fakeAsync(() => {
            expect(component).toBeTruthy();
        }));

        it('should set initial values correctly', fakeAsync(() => {
            component.course = course;
            component._activeConversation = activeConversation;
            component.posts = [examplePost];
        }));

        it('should fetch posts on search input and clear search again on clear button press', fakeAsync(() => {
            const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
            const inputField = fixture.debugElement.query(By.css('#searchInput'));
            inputField.nativeElement.value = 'test';
            inputField.nativeElement.dispatchEvent(new Event('input'));
            tick(301);
            expect(component.searchText).toBe('test');
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
            fixture.detectChanges();

            getFilteredPostSpy.mockClear();
            const clearButton = fixture.debugElement.query(By.css('#clearSearchButton'));
            clearButton.nativeElement.click();
            tick(301);
            expect(component.searchText).toBe('');
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
        }));

        it('should fetch posts on next page fetch', fakeAsync(() => {
            const getFilteredPostSpy = jest.spyOn(metisService, 'getFilteredPosts');
            component.searchText = 'loremIpsum';
            component.totalNumberOfPosts = 10;
            component.fetchNextPage();
            expect(getFilteredPostSpy).toHaveBeenCalledOnce();
        }));

        it('should save the scroll position in sessionStorage', fakeAsync(() => {
            const setItemSpy = jest.spyOn(sessionStorage, 'setItem');
            component.ngOnInit();
            component.saveScrollPosition(15);
            tick(100);
            const expectedKey = `${component.sessionStorageKey}${component._activeConversation?.id}`;
            const expectedValue = '15';
            expect(setItemSpy).toHaveBeenCalledWith(expectedKey, expectedValue);
            expect(setItemSpy).toHaveBeenCalledTimes(2);
        }));

        it('should scroll to the last selected element or fetch next page if not found', fakeAsync(() => {
            const mockMessages = [
                { post: { id: 1 }, elementRef: { nativeElement: { scrollIntoView: jest.fn() } } },
                { post: { id: 2 }, elementRef: { nativeElement: { scrollIntoView: jest.fn() } } },
            ] as unknown as PostingThreadComponent[];
            component.messages = {
                toArray: () => mockMessages,
            } as QueryList<PostingThreadComponent>;

            const fetchNextPageSpy = jest.spyOn(component, 'fetchNextPage').mockImplementation(() => {});
            const existingScrollPosition = 1;

            component.goToLastSelectedElement(existingScrollPosition, false);
            tick();
            expect(fetchNextPageSpy).not.toHaveBeenCalled();

            const nonExistingScrollPosition = 999;
            component.goToLastSelectedElement(nonExistingScrollPosition, false);
            tick();

            expect(fetchNextPageSpy).toHaveBeenCalled();
        }));

        it('should find visible elements at the scroll position and save scroll position', () => {
            // Mock des Containers
            component.content.nativeElement = {
                getBoundingClientRect: jest.fn().mockReturnValue({ top: 0, bottom: 100 }),
                scrollTop: 0,
                scrollHeight: 200,
                removeEventListener: jest.fn(),
            };
            const mockMessages = [
                { post: { id: 1 }, elementRef: { nativeElement: { getBoundingClientRect: jest.fn().mockReturnValue({ top: 10, bottom: 90 }) } } },
                { post: { id: 2 }, elementRef: { nativeElement: { getBoundingClientRect: jest.fn().mockReturnValue({ top: 100, bottom: 200 }) } } },
            ] as unknown as PostingThreadComponent[];
            component.messages.toArray = jest.fn().mockReturnValue(mockMessages);
            component.canStartSaving = true;
            const nextSpy = jest.spyOn(component.scrollSubject, 'next');
            component.findElementsAtScrollPosition();
            expect(component.elementsAtScrollPosition).toEqual([mockMessages[0]]);
            expect(nextSpy).toHaveBeenCalledWith(1);
        });

        it('should not save scroll position if no elements are visible', () => {
            const mockMessages = [
                {
                    post: { id: 1 },
                    elementRef: { nativeElement: { getBoundingClientRect: jest.fn().mockReturnValue({ top: 200, bottom: 300 }) } },
                },
            ] as unknown as PostingThreadComponent[];

            component.messages.toArray = jest.fn().mockReturnValue(mockMessages);
            const nextSpy = jest.spyOn(component.scrollSubject, 'next');
            component.findElementsAtScrollPosition();
            expect(component.elementsAtScrollPosition).toEqual([]);
            expect(nextSpy).not.toHaveBeenCalled();
        });

        it('should scroll to the bottom when a new message is created', fakeAsync(() => {
            component.content.nativeElement.scrollTop = 100;
            fixture.detectChanges();
            component.handleNewMessageCreated();
            tick(300);
            expect(component.content.nativeElement.scrollTop).toBe(component.content.nativeElement.scrollHeight);
        }));

        it('should create empty post with the correct conversation type', fakeAsync(() => {
            const createEmptyPostForContextSpy = jest.spyOn(metisService, 'createEmptyPostForContext').mockReturnValue(new Post());
            component.createEmptyPost();
            expect(createEmptyPostForContextSpy).toHaveBeenCalledOnce();
            const conversation = createEmptyPostForContextSpy.mock.calls[0][0];
            expect(conversation!.type).toEqual(activeConversation.type);
            expect(conversation!.id).toEqual(activeConversation.id);
        }));

        it('should set posts and group them correctly', () => {
            const posts = [
                { id: 1, creationDate: dayjs().subtract(2, 'hours'), author: { id: 1 } } as Post,
                { id: 4, creationDate: dayjs().subtract(3, 'minutes'), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().subtract(1, 'minutes'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs(), author: { id: 2 } } as Post,
            ];

            component.allPosts = posts;
            component.setPosts();

            expect(component.posts).toHaveLength(4);
            expect(component.groupedPosts).toHaveLength(3);

            expect(component.groupedPosts[0].posts).toHaveLength(1);
            expect(component.groupedPosts[0].posts[0].id).toBe(1);
            expect(component.groupedPosts[0].posts[0].isConsecutive).toBeFalse();

            expect(component.groupedPosts[1].posts).toHaveLength(2);
            expect(component.groupedPosts[1].posts[0].id).toBe(4);
            expect(component.groupedPosts[1].posts[0].isConsecutive).toBeFalse();
            expect(component.groupedPosts[1].posts[1].id).toBe(2);
            expect(component.groupedPosts[1].posts[1].isConsecutive).toBeTrue();

            expect(component.groupedPosts[2].posts).toHaveLength(1);
            expect(component.groupedPosts[2].posts[0].id).toBe(3);
            expect(component.groupedPosts[2].posts[0].isConsecutive).toBeFalse();
        });

        it('should not group posts that are exactly 5 minutes apart', () => {
            const posts = [
                { id: 1, creationDate: dayjs().subtract(10, 'minutes'), author: { id: 1 } } as Post,
                { id: 2, creationDate: dayjs().subtract(5, 'minutes'), author: { id: 1 } } as Post,
                { id: 3, creationDate: dayjs(), author: { id: 1 } } as Post,
            ];

            component.allPosts = posts;
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

            jest.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(mockResponse));

            metisService.getForwardedMessagesByIds([1], PostingType.POST)?.subscribe((response) => {
                expect(response.body).toEqual(mockResponse.body);
            });
        });

        if (getAsChannelDTO(activeConversation)?.isAnnouncementChannel) {
            it('should display the "new announcement" button when the conversation is an announcement channel', fakeAsync(() => {
                const announcementButton = fixture.debugElement.query(By.css('.btn.btn-md.btn-primary'));
                expect(announcementButton).toBeTruthy(); // Check if the button is present
                expect(component.isHiddenInputFull).toBeFalsy();
                expect(component.isHiddenInputWithCallToAction).toBeTruthy();

                const modal = fixture.debugElement.query(By.directive(PostCreateEditModalComponent));
                expect(modal).toBeTruthy(); // Check if the modal is present
            }));
        } else {
            it('should display the inline input when the conversation is not an announcement channel', fakeAsync(() => {
                const inlineInput = fixture.debugElement.query(By.directive(MessageInlineInputComponent));
                expect(inlineInput).toBeTruthy(); // Check if the inline input component is present
            }));
        }

        it('should scroll to bottom and set canStartSaving to true when lastScrollPosition is falsy', async () => {
            const scrollToBottomSpy = jest.spyOn(component, 'scrollToBottomOfMessages');

            await component.goToLastSelectedElement(0, false);

            expect(scrollToBottomSpy).toHaveBeenCalledOnce();
            expect(component.canStartSaving).toBeTrue();
        });

        it('should handle posts without forwarded messages gracefully', fakeAsync(() => {
            jest.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [] })));

            component.posts = [{ id: 1, hasForwardedMessages: false } as Post];

            tick();

            expect(component.posts[0].forwardedPosts).toBeUndefined();
            expect(component.posts[0].forwardedAnswerPosts).toBeUndefined();
        }));

        it('should handle forwarded messages with missing source gracefully', fakeAsync(() => {
            const mockForwardedMessages: ForwardedMessage[] = [
                { id: 101, sourceId: 99, sourceType: 'POST' } as unknown as ForwardedMessage,
                { id: 102, sourceId: 100, sourceType: 'ANSWER' } as unknown as ForwardedMessage,
            ];

            jest.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of([]));
            jest.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of([]));

            jest.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));

            component.allPosts = [{ id: 1, hasForwardedMessages: true } as Post];
            component.setPosts();

            tick();

            expect(component.posts[0].forwardedPosts).toEqual([]);
            expect(component.posts[0].forwardedAnswerPosts).toEqual([]);
        }));

        it('should not fetch source posts or answers for empty forwarded messages', fakeAsync(() => {
            const mockForwardedMessages: ForwardedMessage[] = [];

            jest.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of([]));
            jest.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of([]));

            jest.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));

            const getSourcePostsSpy = jest.spyOn(metisService, 'getSourcePostsByIds');
            const getSourceAnswersSpy = jest.spyOn(metisService, 'getSourceAnswerPostsByIds');

            component.allPosts = [{ id: 1, hasForwardedMessages: true } as Post];
            component.setPosts();

            tick();

            expect(getSourcePostsSpy).not.toHaveBeenCalled();
            expect(getSourceAnswersSpy).not.toHaveBeenCalled();
        }));

        it('should correctly assign forwarded posts and answers', fakeAsync(() => {
            const mockForwardedMessages: ForwardedMessage[] = [
                { id: 101, sourceId: 10, sourceType: 'POST' } as unknown as ForwardedMessage,
                { id: 102, sourceId: 11, sourceType: 'ANSWER' } as unknown as ForwardedMessage,
            ];

            const mockSourcePosts: Post[] = [{ id: 10, content: 'Forwarded Post Content', conversation: component._activeConversation as Conversation } as Post];
            const mockSourceAnswerPosts: AnswerPost[] = [{ id: 11, content: 'Forwarded Answer Content', resolvesPost: true } as AnswerPost];

            jest.spyOn(metisService, 'getForwardedMessagesByIds').mockReturnValue(of(new HttpResponse({ body: [{ id: 1, messages: mockForwardedMessages }] })));
            jest.spyOn(metisService, 'getSourcePostsByIds').mockReturnValue(of(mockSourcePosts));
            jest.spyOn(metisService, 'getSourceAnswerPostsByIds').mockReturnValue(of(mockSourceAnswerPosts));

            const postsWithForwarded: Post[] = [
                {
                    id: 1,
                    content: 'Some content...',
                    hasForwardedMessages: true,
                } as Post,
            ];

            component.allPosts = postsWithForwarded;
            component.setPosts();

            tick();
            fixture.detectChanges();
            const getSourcePostsSpy = jest.spyOn(metisService, 'getSourcePostsByIds');
            const getSourceAnswersSpy = jest.spyOn(metisService, 'getSourceAnswerPostsByIds');

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
                        expect(post).toBeNull();
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
                        expect(post).toBeNull();
                    }
                });
            }
        }));

        it('should filter posts to show only pinned posts when showOnlyPinned is true', () => {
            const pinnedPost = { id: 1, displayPriority: 'PINNED' } as Post;
            const regularPost = { id: 2, displayPriority: 'NONE' } as Post;
            component.pinnedPosts = [pinnedPost];
            component.allPosts = [pinnedPost, regularPost];

            runInInjectionContext(fixture.debugElement.injector, () => {
                component.showOnlyPinned = input<boolean>(true);
                component.applyPinnedMessageFilter();
            });

            expect(component.posts).toEqual([pinnedPost]);
        });

        it('should show all posts when showOnlyPinned is false', () => {
            const pinnedPost = { id: 1, displayPriority: 'PINNED' } as Post;
            const regularPost = { id: 2, displayPriority: 'NONE' } as Post;
            component.pinnedPosts = [pinnedPost];
            component.allPosts = [pinnedPost, regularPost];
            runInInjectionContext(fixture.debugElement.injector, () => {
                component.showOnlyPinned = input<boolean>(false);
                component.applyPinnedMessageFilter();
            });

            expect(component.posts).toEqual([pinnedPost, regularPost]);
        });

        it('should call setPosts when showOnlyPinned input changes and it is not the first change', () => {
            const changes = {
                showOnlyPinned: {
                    currentValue: true,
                    previousValue: false,
                    firstChange: false,
                    isFirstChange: () => false,
                },
            };

            const setPostsSpy = jest.spyOn(component, 'setPosts');
            component.ngOnChanges(changes);

            expect(setPostsSpy).toHaveBeenCalled();
        });

        it('should not call setPosts when showOnlyPinned input changes for the first time', () => {
            const changes = {
                showOnlyPinned: {
                    currentValue: true,
                    previousValue: undefined,
                    firstChange: true,
                    isFirstChange: () => true,
                },
            };

            const setPostsSpy = jest.spyOn(component, 'setPosts');
            component.ngOnChanges(changes);

            expect(setPostsSpy).not.toHaveBeenCalled();
        });

        it('should not call setPosts when showOnlyPinned input does not change', () => {
            const changes = {
                unrelatedInput: {
                    currentValue: true,
                    previousValue: false,
                    firstChange: false,
                    isFirstChange: () => false,
                },
            };

            const setPostsSpy = jest.spyOn(component, 'setPosts');
            component.ngOnChanges(changes);

            expect(setPostsSpy).not.toHaveBeenCalled();
        });

        it('should subscribe to pinned posts on init and emit pinnedCount', fakeAsync(() => {
            const pinnedPostsStub = [{ id: 10, displayPriority: 'PINNED' }] as Post[];
            const pinnedPostsSubject = new BehaviorSubject<Post[]>([]);

            const pinnedCountSpy = jest.spyOn(component.pinnedCount, 'emit');
            jest.spyOn(metisService, 'getPinnedPosts').mockReturnValue(pinnedPostsSubject.asObservable());

            component.ngOnInit();

            pinnedPostsSubject.next(pinnedPostsStub);
            tick();

            expect(component.pinnedPosts).toEqual(pinnedPostsStub);
            expect(pinnedCountSpy).toHaveBeenCalledWith(pinnedPostsStub.length);
        }));

        it('should fetch pinned posts in onActiveConversationChange and emit pinnedCount', fakeAsync(() => {
            const pinnedPostsStub = [{ id: 77, displayPriority: 'PINNED' }] as Post[];
            const pinnedCountSpy = jest.spyOn(component.pinnedCount, 'emit');
            jest.spyOn(metisService, 'fetchAllPinnedPosts').mockReturnValue(of(pinnedPostsStub));

            component._activeConversation = { id: 123, type: ConversationType.CHANNEL };
            component.course = { id: 1 } as Course;
            component.searchInput = {
                nativeElement: { value: '' },
            } as ElementRef;

            component['onActiveConversationChange']();
            tick();

            expect(component.pinnedPosts).toEqual(pinnedPostsStub);
            expect(pinnedCountSpy).toHaveBeenCalledWith(pinnedPostsStub.length);
        }));
    });
});

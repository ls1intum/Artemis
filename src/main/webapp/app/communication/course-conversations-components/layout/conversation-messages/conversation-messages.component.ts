import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    ElementRef,
    EventEmitter,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    QueryList,
    SimpleChanges,
    ViewChild,
    ViewChildren,
    ViewEncapsulation,
    effect,
    inject,
    input,
    output,
} from '@angular/core';
import { faCircleNotch, faEnvelope, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Subject, forkJoin, map, takeUntil } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { MetisService } from 'app/communication/service/metis.service';
import { Channel, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChat, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { ButtonType } from 'app/shared/components/button/button.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { OneToOneChat, isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import { User } from 'app/core/user/user.model';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { NgClass } from '@angular/common';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ForwardedMessageDTO } from 'app/communication/shared/entities/forwarded-message.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { canCreateNewMessageInConversation } from 'app/communication/conversations/conversation-permissions.utils';

interface PostGroup {
    author: User | undefined;
    posts: Post[];
}

@Component({
    selector: 'jhi-conversation-messages',
    templateUrl: './conversation-messages.component.html',
    styleUrls: ['./conversation-messages.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        FaIconComponent,
        TranslateDirective,
        InfiniteScrollDirective,
        NgClass,
        PostingThreadComponent,
        PostCreateEditModalComponent,
        MessageInlineInputComponent,
        ArtemisTranslatePipe,
    ],
})
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    metisService = inject(MetisService);
    metisConversationService = inject(MetisConversationService);
    cdr = inject(ChangeDetectorRef);

    private ngUnsubscribe = new Subject<void>();
    readonly sessionStorageKey = 'conversationId.scrollPosition.';

    readonly PageType = PageType;
    readonly ButtonType = ButtonType;

    private scrollDebounceTime = 100; // ms
    scrollSubject = new Subject<number>();
    canStartSaving = false;
    createdNewMessage = false;

    @Output() openThread = new EventEmitter<Post>();

    @ViewChild('searchInput') searchInput: ElementRef;
    @ViewChildren('postingThread') messages: QueryList<PostingThreadComponent>;
    @ViewChild('container') content: ElementRef;

    @Input() course?: Course;
    @Input() searchbarCollapsed = false;
    @Input() contentHeightDev = false;
    showOnlyPinned = input<boolean>(false);
    pinnedCount = output<number>();
    pinnedPosts: Post[] = [];

    readonly focusPostId = input<number | undefined>(undefined);
    readonly openThreadOnFocus = input<boolean>(false);

    getAsChannel = getAsChannelDTO;

    canCreateNewMessageInConversation = canCreateNewMessageInConversation;

    previousScrollDistanceFromTop: number;
    // as set for the css class '.posting-infinite-scroll-container'
    messagesContainerHeight = 700;
    currentPostContextFilter?: PostContextFilter;
    private readonly search$ = new Subject<string>();
    searchText = '';
    _activeConversation?: ConversationDTO;
    readonly onNavigateToPost = output<Posting>();

    elementsAtScrollPosition: PostingThreadComponent[];
    newPost?: Post;
    posts: Post[] = [];
    allPosts: Post[] = [];
    groupedPosts: PostGroup[] = [];
    totalNumberOfPosts = 0;
    page = 1;
    public isFetchingPosts = true;
    // Icons
    faTimes = faTimes;
    faSearch = faSearch;
    faEnvelope = faEnvelope;
    faCircleNotch = faCircleNotch;
    isMobile = false;
    isHiddenInputWithCallToAction = false;
    isHiddenInputFull = false;
    focusOnPostId: number | undefined = undefined;
    isOpenThreadOnFocus = false;

    private layoutService: LayoutService = inject(LayoutService);

    constructor() {
        effect(() => {
            this.focusOnPostId = this.focusPostId();
            this.isOpenThreadOnFocus = this.openThreadOnFocus();
        });
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes['showOnlyPinned'] && !changes['showOnlyPinned'].firstChange) {
            this.setPosts();
        }
    }

    applyPinnedMessageFilter(): void {
        if (this.showOnlyPinned()) {
            this.posts = this.pinnedPosts;
        } else {
            this.posts = [...this.allPosts];
        }
        this.cdr.detectChanges();
    }

    ngOnInit(): void {
        this.subscribeToSearch();
        this.subscribeToMetis();
        this.subscribeToActiveConversation();
        this.setupScrollDebounce();
        this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);
        this.layoutService
            .subscribeToLayoutChanges()
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {
                this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);
            });

        this.metisService
            .getPinnedPosts()
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((pinnedPosts) => {
                this.pinnedPosts = pinnedPosts;
                this.pinnedCount.emit(pinnedPosts.length);
                this.cdr.detectChanges();
            });

        this.metisService.fetchAllPinnedPosts(this._activeConversation!.id!).subscribe();
        this.cdr.detectChanges();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            if (this._activeConversation && getAsChannelDTO(conversation)?.isArchived !== getAsChannelDTO(this._activeConversation)?.isArchived) {
                this._activeConversation = conversation;
            }
            // This statement avoids a bug that reloads the messages when the conversation is already displayed
            if (conversation && this._activeConversation?.id === conversation.id) {
                return;
            }
            this._activeConversation = conversation;
            this.onActiveConversationChange();
        });
    }

    private subscribeToSearch() {
        this.search$
            .pipe(
                debounceTime(300),
                distinctUntilChanged(),
                map((searchText: string | null | undefined) => {
                    const cleanedSearchText = searchText !== null && searchText !== undefined ? searchText : '';
                    return cleanedSearchText.trim().toLowerCase();
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (searchText: string) => {
                    this.searchText = searchText;
                    this.onSearch();
                },
            });
    }

    ngAfterViewInit() {
        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            if (!this.createdNewMessage && this.posts.length > 0) {
                this.scrollToStoredId();
            } else {
                this.createdNewMessage = false;
            }
        });
        this.content.nativeElement.addEventListener('scroll', () => {
            this.findElementsAtScrollPosition();
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.scrollSubject.complete();
        this.content?.nativeElement.removeEventListener('scroll', this.saveScrollPosition);
    }

    private scrollToStoredId() {
        let savedScrollId;
        if (this.focusOnPostId) {
            savedScrollId = this.focusOnPostId + '';
        } else {
            savedScrollId = sessionStorage.getItem(this.sessionStorageKey + this._activeConversation?.id) ?? '';
        }
        requestAnimationFrame(() => this.goToLastSelectedElement(parseInt(savedScrollId, 10), this.isOpenThreadOnFocus));
    }

    private onActiveConversationChange() {
        if (this._activeConversation !== undefined && this.getAsChannel(this._activeConversation)?.isAnnouncementChannel) {
            this.isHiddenInputFull = !canCreateNewMessageInConversation(this._activeConversation);
            this.isHiddenInputWithCallToAction = canCreateNewMessageInConversation(this._activeConversation);
        } else {
            this.isHiddenInputFull = false;
            this.isHiddenInputWithCallToAction = false;
        }

        if (this.course && this._activeConversation) {
            if (this.searchInput) {
                this.searchInput.nativeElement.value = '';
                this.searchText = '';
            }
            this.canStartSaving = false;
            this.onSearch();
            this.createEmptyPost();
            this.metisService.fetchAllPinnedPosts(this._activeConversation!.id!).subscribe({
                next: (pinnedPosts: Post[]) => {
                    this.pinnedPosts = pinnedPosts;
                    this.pinnedCount.emit(pinnedPosts.length);
                },
            });
        }
    }

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.allPosts = posts;
            this.setPosts();
            this.isFetchingPosts = false;
        });
        this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
    }

    private refreshMetisConversationPostContextFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course?.id,
            conversationId: this._activeConversation?.id,
            searchText: this.searchText ? this.searchText.trim() : undefined,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
            pagingEnabled: true,
            page: this.page - 1,
            pageSize: 50,
        };
    }

    private groupPosts(): void {
        if (!this.posts || this.posts.length === 0) {
            this.groupedPosts = [];
            return;
        }

        const sortedPosts = this.posts.sort((a, b) => {
            return a.creationDate!.valueOf() - b.creationDate!.valueOf();
        });

        const groups: PostGroup[] = [];
        let currentGroup: PostGroup = {
            author: sortedPosts[0].author,
            posts: [{ ...sortedPosts[0], isConsecutive: false }],
        };

        for (let i = 1; i < sortedPosts.length; i++) {
            const currentPost = sortedPosts[i];
            const lastPostInGroup = currentGroup.posts[currentGroup.posts.length - 1];

            let timeDiff = Number.MAX_SAFE_INTEGER;
            if (currentPost.creationDate && lastPostInGroup.creationDate) {
                timeDiff = currentPost.creationDate.diff(lastPostInGroup.creationDate, 'minute');
            }

            if (currentPost.author?.id === currentGroup.author?.id && timeDiff < 5 && timeDiff >= 0) {
                currentGroup.posts.push({ ...currentPost, isConsecutive: true }); // consecutive post
            } else {
                groups.push(currentGroup);
                currentGroup = {
                    author: currentPost.author,
                    posts: [{ ...currentPost, isConsecutive: false }],
                };
            }
        }

        groups.push(currentGroup);

        this.groupedPosts = groups;
        this.cdr.detectChanges();
    }

    setPosts(): void {
        if (this.content) {
            this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        }

        this.applyPinnedMessageFilter();

        this.posts = this.posts.slice().reverse();

        const postIdsWithForwardedMessages = this.posts.filter((post) => post.hasForwardedMessages && post.id !== undefined).map((post) => post.id) as number[];

        if (postIdsWithForwardedMessages.length > 0) {
            this.metisService.getForwardedMessagesByIds(postIdsWithForwardedMessages, PostingType.POST)?.subscribe((response) => {
                const forwardedMessagesGroups = response.body;

                if (forwardedMessagesGroups) {
                    const map = new Map<number, ForwardedMessageDTO[]>(forwardedMessagesGroups.map((group) => [group.id, group.messages]));

                    const sourcePostIds: number[] = [];
                    const sourceAnswerIds: number[] = [];

                    map.forEach((messages) => {
                        messages.forEach((message) => {
                            if (message.sourceType?.toString() === 'POST' && message.sourceId) {
                                sourcePostIds.push(message.sourceId);
                            } else if (message.sourceType?.toString() === 'ANSWER' && message.sourceId) {
                                sourceAnswerIds.push(message.sourceId);
                            }
                        });
                    });

                    const sourceRequests = [];
                    if (sourcePostIds.length > 0) {
                        sourceRequests.push(this.metisService.getSourcePostsByIds(sourcePostIds));
                    }
                    if (sourceAnswerIds.length > 0) {
                        sourceRequests.push(this.metisService.getSourceAnswerPostsByIds(sourceAnswerIds));
                    }

                    if (sourceRequests.length > 0) {
                        forkJoin(sourceRequests).subscribe((responses) => {
                            let fetchedPosts: Post[] = [];
                            let fetchedAnswerPosts: AnswerPost[] = [];

                            responses.forEach((response) => {
                                if (Array.isArray(response)) {
                                    if (response.length > 0) {
                                        if ((response[0] as Post).conversation !== undefined) {
                                            fetchedPosts = response as Post[];
                                        } else if ((response[0] as AnswerPost).resolvesPost !== undefined) {
                                            fetchedAnswerPosts = response as AnswerPost[];
                                        }
                                    }
                                }
                            });

                            const fetchedPostIds = new Set(fetchedPosts.map((post) => post.id));
                            const fetchedAnswerPostIds = new Set(fetchedAnswerPosts.map((answerPost) => answerPost.id));

                            this.posts = this.posts.map((post) => {
                                const forwardedMessages = map.get(post.id!) || [];
                                post.forwardedPosts = forwardedMessages
                                    .filter((message) => message.sourceType?.toString() === 'POST')
                                    .map((message) => {
                                        if (message.sourceId && !fetchedPostIds.has(message.sourceId)) {
                                            // A source post has not been found so it was most likely deleted.
                                            // We return null to indicate a missing post and handle it later (see forwarded-message.component)
                                            return null;
                                        }
                                        return fetchedPosts.find((fetchedPost) => fetchedPost.id === message.sourceId);
                                    })
                                    .filter((post) => post !== undefined) as Post[];

                                post.forwardedAnswerPosts = forwardedMessages
                                    .filter((message) => message.sourceType?.toString() === 'ANSWER')
                                    .map((message) => {
                                        if (message.sourceId && !fetchedAnswerPostIds.has(message.sourceId)) {
                                            // A source post has not been found so it was most likely deleted, therefore we return null.
                                            // We return null to indicate a missing post and handle it later (see forwarded-message.component)
                                            return null;
                                        }
                                        return fetchedAnswerPosts.find((fetchedAnswerPost) => fetchedAnswerPost.id === message.sourceId);
                                    })
                                    .filter((answerPost) => answerPost !== undefined) as AnswerPost[];
                                return post;
                            });

                            this.groupPosts();
                            this.cdr.markForCheck();
                        });
                    } else {
                        // No source posts or answer posts to fetch
                        this.groupPosts();
                    }
                } else {
                    // No forwarded messages found
                    this.groupPosts();
                }
            });
        } else {
            // No posts with forwarded messages
            this.groupPosts();
        }
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        let addBuffer = 0;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
            addBuffer = 50;
        } else if (!this.canStartSaving) {
            this.canStartSaving = true;
        }
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollTop + addBuffer;
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        this.refreshMetisConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts = true; // will be set to false in subscription
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate, this._activeConversation);
        }
    }

    onSearch(): void {
        this.page = 1;
        this.commandMetisToFetchPosts(true);
    }

    createEmptyPost(): void {
        this.newPost = this.createEmptyPostInMetis();
    }

    private createEmptyPostInMetis() {
        if (!this._activeConversation) {
            return undefined;
        }
        let conversation: Conversation;
        if (isChannelDTO(this._activeConversation)) {
            const channel = new Channel();
            channel.isAnnouncementChannel = this._activeConversation.isAnnouncementChannel;
            conversation = channel;
        } else if (isGroupChatDTO(this._activeConversation)) {
            conversation = new GroupChat();
        } else if (isOneToOneChatDTO(this._activeConversation)) {
            conversation = new OneToOneChat();
        } else {
            throw new Error('Conversation type not supported');
        }
        conversation.id = this._activeConversation.id;
        this.refreshMetisConversationPostContextFilter();
        return this.metisService.createEmptyPostForContext(conversation);
    }

    postsGroupTrackByFn = (_index: number, post: PostGroup): string => 'grp_' + post.posts.map((p) => p.id?.toString()).join('_');

    postsTrackByFn = (_index: number, post: Post): string => 'post_' + post.id!;

    setPostForThread(post: Post) {
        this.openThread.emit(post);
    }
    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        // Use setTimeout to ensure the scroll happens after the new message is rendered
        requestAnimationFrame(() => {
            this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
        });
    }

    onSearchQueryInput($event: Event) {
        const searchTerm = ($event.target as HTMLInputElement).value?.trim().toLowerCase() ?? '';
        this.search$.next(searchTerm);
    }

    clearSearchInput() {
        if (this.searchInput) {
            this.searchInput.nativeElement.value = '';
            this.searchInput.nativeElement.dispatchEvent(new Event('input'));
        }
    }

    private setupScrollDebounce(): void {
        this.scrollSubject.pipe(debounceTime(this.scrollDebounceTime), takeUntil(this.ngUnsubscribe)).subscribe((postId) => {
            if (this._activeConversation?.id) {
                sessionStorage.setItem(this.sessionStorageKey + this._activeConversation.id, postId.toString());
            }
        });
    }

    saveScrollPosition = (postId: number) => {
        this.scrollSubject.next(postId);
    };

    handleNewMessageCreated() {
        this.createdNewMessage = true;
        this.createEmptyPost();
        this.scrollToBottomOfMessages();
    }

    async goToLastSelectedElement(lastScrollPosition: number, isOpenThread: boolean) {
        if (!lastScrollPosition) {
            this.scrollToBottomOfMessages();
            this.canStartSaving = true;
            return;
        }
        const messageArray = this.messages.toArray();
        const element = messageArray.find((message) => message.post.id === lastScrollPosition); // Suchen nach dem Post

        if (!element) {
            this.fetchNextPage();
        } else {
            // We scroll to the element with a slight buffer to ensure its fully visible (-10)
            this.content.nativeElement.scrollTop = Math.max(0, element.elementRef.nativeElement.offsetTop - 10);
            this.canStartSaving = true;
            if (isOpenThread) {
                this.openThread.emit(element.post);
            }
            this.focusOnPostId = undefined;
            this.isOpenThreadOnFocus = false;
        }
    }

    findElementsAtScrollPosition() {
        const messageArray = this.messages.toArray();
        const containerRect = this.content.nativeElement.getBoundingClientRect();
        const visibleMessages = [];
        for (const message of messageArray) {
            if (!message.elementRef?.nativeElement || !message.post?.id) continue;
            const rect = message.elementRef.nativeElement.getBoundingClientRect();
            if (rect.top >= containerRect.top && rect.bottom <= containerRect.bottom) {
                visibleMessages.push(message);
                break; // Only need the first visible message
            }
        }
        this.elementsAtScrollPosition = visibleMessages;
        if (this.elementsAtScrollPosition && this.elementsAtScrollPosition.length > 0 && this.canStartSaving) {
            this.saveScrollPosition(this.elementsAtScrollPosition[0].post.id!);
        }
    }

    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}

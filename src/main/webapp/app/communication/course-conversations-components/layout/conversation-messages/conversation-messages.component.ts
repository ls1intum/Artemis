import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
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
import { toSignal } from '@angular/core/rxjs-interop';
import { faArrowDown, faCircleNotch, faEnvelope, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Observable, Subject, forkJoin, map, takeUntil } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { MetisService } from 'app/communication/service/metis.service';
import { Channel, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChat, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { OneToOneChat, isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { User } from 'app/core/user/user.model';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { NgClass } from '@angular/common';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { ForwardedMessageDTO, ForwardedMessagesGroupDTO } from 'app/communication/shared/entities/forwarded-message.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { canCreateNewMessageInConversation } from 'app/communication/conversations/conversation-permissions.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

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
        ButtonComponent,
    ],
})
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy, OnChanges {
    private sessionStorageService = inject(SessionStorageService);
    private breakpointObserver = inject(BreakpointObserver);
    metisService = inject(MetisService);
    metisConversationService = inject(MetisConversationService);
    cdr = inject(ChangeDetectorRef);

    private ngUnsubscribe = new Subject<void>();
    readonly isMobile = toSignal(this.breakpointObserver.observe([Breakpoints.Handset]).pipe(map((result) => result.matches)), {
        initialValue: this.breakpointObserver.isMatched(Breakpoints.Handset),
    });
    readonly sessionStorageKey = 'conversationId.scrollPosition.';

    readonly PageType = PageType;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    private scrollDebounceTime = 100; // ms
    scrollSubject = new Subject<number>();
    canStartSaving = false;
    createdNewMessage = false;

    @Output() openThread = new EventEmitter<Post>();

    @ViewChildren('postingThread') messages: QueryList<PostingThreadComponent>;
    @ViewChild('container') content: ElementRef;

    @Input() course?: Course;
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
    unreadPosts: Post[] = [];
    groupedPosts: PostGroup[] = [];
    totalNumberOfPosts = 0;
    page = 1;
    public isFetchingPosts = true;
    currentUser: User;
    firstUnreadPostId: number | undefined;
    unreadPostsCount: number = 0;
    atNewPostPosition = false;

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faEnvelope = faEnvelope;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faArrowDown = faArrowDown;

    isHiddenInputWithCallToAction = false;
    isHiddenInputFull = false;
    focusOnPostId: number | undefined = undefined;
    isOpenThreadOnFocus = false;

    accountService = inject(AccountService);

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

    /**
     * Applies pinned message filter based on current toggle state.
     * If the toggle is active, only pinned posts are shown; otherwise, all posts.
     */
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

        // Fetch and subscribe to pinned posts, emit count to parent component
        this.metisService
            .getPinnedPosts()
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((pinnedPosts) => {
                this.pinnedPosts = pinnedPosts;
                this.pinnedCount.emit(pinnedPosts.length);
                this.cdr.detectChanges();
            });

        this.accountService.identity().then((user: User) => {
            this.currentUser = user!;
        });

        // Ensure that all pinned posts are fetched when the component is initialized
        this.metisService.fetchAllPinnedPosts(this._activeConversation!.id!).subscribe();
        this.cdr.detectChanges();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
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
            }
        });
        this.content.nativeElement.addEventListener('scroll', () => {
            this.findElementsAtScrollPosition();
        });

        const el = this.content.nativeElement;
        const observer = new MutationObserver(() => {
            this.findElementsAtScrollPosition();
        });
        observer.observe(el, {
            childList: true,
            subtree: true,
        });
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.scrollSubject.complete();
        this.content?.nativeElement.removeEventListener('scroll', this.saveScrollPosition);
    }

    private scrollToStoredId() {
        let savedScrollId: number | undefined;
        if (this.focusOnPostId) {
            savedScrollId = this.focusOnPostId;
        } else {
            const activeConversationId = this._activeConversation?.id;
            savedScrollId = activeConversationId ? this.sessionStorageService.retrieve<number>(this.sessionStorageKey + activeConversationId) : undefined;
        }
        if (savedScrollId) {
            requestAnimationFrame(() => this.goToLastSelectedElement(savedScrollId, this.isOpenThreadOnFocus));
        }
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
            this.computeLastReadState();
        });
        this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
    }

    private refreshMetisConversationPostContextFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course?.id,
            conversationIds: this._activeConversation?.id ? [this._activeConversation.id] : undefined,
            searchText: this.searchText ? this.searchText.trim() : undefined,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
            pagingEnabled: true,
            page: this.page - 1,
            pageSize: 50,
        };
    }

    /**
     * Groups posts by author and timestamp proximity (within 5 minutes),
     * marking consecutive posts for visual grouping.
     */
    private groupPosts(): void {
        // If there are no posts, clear groupedPosts and exit.
        if (!this.posts || this.posts.length === 0) {
            this.groupedPosts = [];
            return;
        }

        // Sort posts by the creation date
        const sortedPosts = [...this.posts].sort((a, b) => {
            return a.creationDate!.valueOf() - b.creationDate!.valueOf();
        });

        // Compute new grouping based on current posts.
        const computedGroups: PostGroup[] = [];
        let currentGroup: PostGroup | undefined = undefined;

        sortedPosts.forEach((post) => {
            if (!currentGroup) {
                // Start new group if none exists.
                currentGroup = { author: post.author, posts: [{ ...post, isConsecutive: false }] };
                return;
            }

            const lastPost = currentGroup.posts[currentGroup.posts.length - 1];
            const currentDate = post.creationDate;
            const lastDate = lastPost.creationDate;

            let timeDiff = Number.MAX_SAFE_INTEGER;
            if (currentDate && lastDate) {
                timeDiff = currentDate.diff(lastDate, 'minute');
            }

            if (this.isAuthorEqual(currentGroup, { author: post.author, posts: [] }) && timeDiff < 5 && timeDiff >= 0) {
                currentGroup.posts.push({ ...post, isConsecutive: true });
            } else {
                computedGroups.push(currentGroup);
                currentGroup = { author: post.author, posts: [{ ...post, isConsecutive: false }] };
            }
        });
        if (currentGroup) {
            computedGroups.push(currentGroup);
        }

        // Update existing groups in place if possible.
        if (this.groupedPosts.length === computedGroups.length) {
            computedGroups.forEach((g, i) => {
                if (this.groupedPosts[i].author?.id === g.author?.id) {
                    // If the group belongs to the same author, update its posts array in place.
                    this.groupedPosts[i].posts.splice(0, this.groupedPosts[i].posts.length, ...g.posts);
                } else {
                    // If group identity has changed, replace the group.
                    this.groupedPosts[i] = g;
                }
            });
        } else {
            this.groupedPosts = computedGroups;
        }

        // Trigger Angular change detection.
        this.cdr.detectChanges();
    }

    private isAuthorEqual(groupA: PostGroup, groupB: PostGroup): boolean {
        // Both groups are equal if neither has an author; otherwise, they are not
        if (!groupA.author || !groupB.author) {
            return !groupA.author && !groupB.author;
        }
        return groupA.author.id === groupB.author.id;
    }

    /**
     * Sets and prepares the list of posts for display.
     * Applies pinned message filters, loads forwarded messages and their sources, and groups posts for rendering.
     */
    setPosts(): void {
        // Saves the current scroll position relative to the bottom.
        if (this.content) {
            this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        }
        this.applyPinnedMessageFilter();
        this.reversePosts();

        const postIds = this.getPostIdsWithForwardedMessages();

        if (postIds.length === 0) {
            this.groupPosts();
            return;
        }
        // only fetch forwarded messages if there are any
        this.fetchForwardedMessages(postIds);
    }

    private fetchForwardedMessages = (postIds: number[]) => {
        this.metisService.getForwardedMessagesByIds(postIds, PostingType.POST)?.subscribe((response) => {
            const forwardedMessagesGroups = response.body;

            if (!forwardedMessagesGroups) {
                this.groupPosts();
                return;
            }

            const forwardedMessagesMap = this.mapForwardedMessages(forwardedMessagesGroups);
            // collect post and answer post ids from the forwarded messages to be fetched
            const { sourcePostIds, sourceAnswerIds } = this.collectSourceIds(forwardedMessagesMap);

            const requests = this.buildSourceRequests(sourcePostIds, sourceAnswerIds);

            if (requests.length === 0) {
                this.groupPosts();
                return;
            }

            // fetch the actual information and wait until all responses are received before grouping the posts
            forkJoin(requests).subscribe((responses) => {
                const { fetchedPosts, fetchedAnswerPosts } = this.extractFetchedSources(responses);

                this.posts = this.attachForwardedMessages(this.posts, forwardedMessagesMap, fetchedPosts, fetchedAnswerPosts);

                this.groupPosts();
                this.cdr.markForCheck();
                if (this.createdNewMessage) {
                    this.scrollToBottomOfMessages();
                    this.createdNewMessage = false;
                }
            });
        });
    };

    /**
     * Reverses the posts array to maintain chronological order.
     */
    private reversePosts(): void {
        this.posts = this.posts.slice().reverse();
    }

    /**
     * Filters posts that contain forwarded messages and have a valid ID.
     */
    private getPostIdsWithForwardedMessages(): number[] {
        return this.posts.filter((post) => post.hasForwardedMessages && post.id !== undefined).map((post) => post.id!) as number[];
    }

    /**
     * Converts list of forwarded message groups to a map from post ID to message list.
     */
    private mapForwardedMessages(groups: ForwardedMessagesGroupDTO[]): Map<number, ForwardedMessageDTO[]> {
        return new Map(groups.map((group) => [group.id!, group.messages!]));
    }

    /**
     * Extracts all source post and answer post IDs from forwarded messages.
     */
    private collectSourceIds(map: Map<number, ForwardedMessageDTO[]>): {
        sourcePostIds: number[];
        sourceAnswerIds: number[];
    } {
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

        return { sourcePostIds, sourceAnswerIds };
    }

    /**
     * Builds requests to fetch the source posts and answer posts based on their IDs.
     * Wraps observables to emit nothing if inputs are invalid or expected to return undefined.
     */
    private buildSourceRequests(sourcePostIds: number[], sourceAnswerIds: number[]): Observable<Posting[] | undefined>[] {
        const requests: Observable<Posting[] | undefined>[] = [];

        if (sourcePostIds.length > 0) {
            requests.push(this.metisService.getSourcePostsByIds(sourcePostIds));
        }

        if (sourceAnswerIds.length > 0) {
            requests.push(this.metisService.getSourceAnswerPostsByIds(sourceAnswerIds));
        }

        return requests;
    }

    /**
     * Extracts fetched post and answer post arrays from forkJoin responses.
     */
    private extractFetchedSources(responses: (Posting[] | undefined)[]): {
        fetchedPosts: Post[];
        fetchedAnswerPosts: AnswerPost[];
    } {
        let fetchedPosts: Post[] = [];
        let fetchedAnswerPosts: AnswerPost[] = [];

        responses.forEach((response) => {
            if (Array.isArray(response) && response.length > 0) {
                const first = response[0];
                if ((first as Post).conversation !== undefined) {
                    fetchedPosts = response as Post[];
                } else if ((first as AnswerPost).resolvesPost !== undefined) {
                    fetchedAnswerPosts = response as AnswerPost[];
                }
            }
        });

        return { fetchedPosts, fetchedAnswerPosts };
    }

    /**
     * Attaches forwarded posts and forwarded answer posts to their corresponding post.
     */
    private attachForwardedMessages(posts: Post[], messageMap: Map<number, ForwardedMessageDTO[]>, fetchedPosts: Post[], fetchedAnswerPosts: AnswerPost[]): Post[] {
        const fetchedPostIds = new Set(fetchedPosts.map((post) => post.id));
        const fetchedAnswerPostIds = new Set(fetchedAnswerPosts.map((answerPost) => answerPost.id));

        return posts.map((post) => {
            const forwardedMessages = messageMap.get(post.id!) || [];

            post.forwardedPosts = forwardedMessages
                .filter((m) => m.sourceType?.toString() === 'POST')
                .map((m) => (m.sourceId && fetchedPostIds.has(m.sourceId) ? fetchedPosts.find((p) => p.id === m.sourceId) : undefined));

            post.forwardedAnswerPosts = forwardedMessages
                .filter((m) => m.sourceType?.toString() === 'ANSWER')
                .map((m) => (m.sourceId && fetchedAnswerPostIds.has(m.sourceId) ? fetchedAnswerPosts.find((a) => a.id === m.sourceId) : undefined));

            return post;
        });
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

    private setupScrollDebounce(): void {
        this.scrollSubject.pipe(debounceTime(this.scrollDebounceTime), takeUntil(this.ngUnsubscribe)).subscribe((postId) => {
            const activeConversationId = this._activeConversation?.id;
            if (activeConversationId) {
                this.sessionStorageService.store<number>(this.sessionStorageKey + activeConversationId, postId);
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
        this.setFirstUnreadPostId();
        this.atNewPostPosition = this.isAnyUnreadPostVisible();
        this.cdr.detectChanges();
    }

    /**
     * Emits navigation event to a specific post (used by forwarded post component).
     */
    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }

    private computeLastReadState(): void {
        this.unreadPosts = this.getUnreadPosts();
        this.unreadPostsCount = this.unreadPosts.length;
    }

    /**
     * Returns a list of posts that were created after the lastReadDate
     * and were not authored by the current user (i.e. true unread posts).
     */
    private getUnreadPosts(): Post[] {
        const lastReadDate = this._activeConversation?.lastReadDate;
        if (!lastReadDate || !this.allPosts?.length) {
            return [];
        }

        const sortedPosts = [...this.allPosts].sort((a, b) => a.creationDate!.diff(b.creationDate!));
        const indexFirstRelevantPost = sortedPosts.findIndex((post) => post.creationDate?.isAfter(lastReadDate) && post.author?.id !== this.currentUser.id);
        return indexFirstRelevantPost >= 0 ? sortedPosts.slice(indexFirstRelevantPost) : [];
    }

    /**
     * Scrolls the container to the first unread post (top-aligned),
     * if it is not currently visible. If the post is taller than the container,
     * a small offset is applied to keep the line above visible.
     */
    scrollToFirstUnreadPostIfNotVisible(): void {
        const rects = this.getBoundingRectsForFirstUnreadPost();
        if (!rects) {
            return;
        }

        const component = this.messages.find((m) => m.post.id === this.firstUnreadPostId);
        if (!component?.elementRef?.nativeElement) {
            return;
        }

        const containerElement = this.content.nativeElement;
        const { postRect, containerRect } = rects;

        const isVisible = postRect.top >= containerRect.top && postRect.bottom <= containerRect.bottom;
        const postIsTallerThanContainer = postRect.bottom - postRect.top > containerRect.bottom - containerRect.top;
        //15 is an arbitrary to ensure the "new" line above the first unread post is visible
        const scrollOffset = postIsTallerThanContainer ? 15 : 0;

        if (!isVisible) {
            requestAnimationFrame(() => {
                const offsetTop = component.elementRef.nativeElement.offsetTop;
                containerElement.scrollTop = Math.max(offsetTop - scrollOffset, 0);
            });
        }
    }

    /**
     * Returns the bounding rectangles of the given post and the scroll container.
     * Returns `undefined` if the post or container is not available in the DOM.
     */
    private getBoundingRectsForPost(postId: number): { postRect: DOMRect; containerRect: DOMRect } | undefined {
        if (!this.content?.nativeElement) {
            return undefined;
        }

        const component = this.messages.find((m) => m.post.id === postId);
        if (!component?.elementRef?.nativeElement) {
            return undefined;
        }

        const postRect = component.elementRef.nativeElement.getBoundingClientRect();
        const containerRect = this.content.nativeElement.getBoundingClientRect();

        return { postRect, containerRect };
    }

    /**
     * Returns the bounding rectangles of the first unread post and the scroll container.
     * Returns `undefined` if the post or container is not available in the DOM.
     */
    private getBoundingRectsForFirstUnreadPost(): { postRect: DOMRect; containerRect: DOMRect } | undefined {
        if (!this.unreadPosts.length || !this.firstUnreadPostId) {
            return undefined;
        }

        return this.getBoundingRectsForPost(this.firstUnreadPostId);
    }

    private isPostVisible(postId: number): boolean {
        const rects = this.getBoundingRectsForPost(postId);
        if (!rects) {
            return false;
        }

        const { postRect, containerRect } = rects;

        return postRect.bottom > containerRect.top && postRect.top < containerRect.bottom;
    }

    private isAnyUnreadPostVisible(): boolean {
        if (!this.unreadPosts?.length) {
            return false;
        }

        return this.unreadPosts.some((unreadPost) => this.isPostVisible(unreadPost.id!));
    }

    private setFirstUnreadPostId(): void {
        if (this.unreadPosts.length > 0) {
            this.firstUnreadPostId = this.unreadPosts[0].id;
        } else {
            this.firstUnreadPostId = undefined;
        }
    }
}

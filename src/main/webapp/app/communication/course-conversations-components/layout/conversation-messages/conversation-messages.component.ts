import { BreakpointObserver } from '@angular/cdk/layout';
import {
    AfterViewInit,
    Component,
    ElementRef,
    OnDestroy,
    OnInit,
    ViewEncapsulation,
    afterRenderEffect,
    effect,
    inject,
    input,
    output,
    signal,
    untracked,
    viewChild,
    viewChildren,
} from '@angular/core';
import { faArrowDown, faCircleNotch, faEnvelope, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Observable, Subject, catchError, forkJoin, map, of, takeUntil } from 'rxjs';
import { Post } from 'app/communication/shared/entities/post.model';
import { Course } from 'app/course/shared/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection, getUnreadPostsByLastReadDate } from 'app/communication/communication.util';
import { CommunicationService } from 'app/communication/service/communication.service';
import { Channel, getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { GroupChat, isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared-ui/components/buttons/button/button.component';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { OneToOneChat, isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { User } from 'app/account/user/user.model';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';
import { NgClass } from '@angular/common';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { ForwardedMessageDTO, ForwardedMessagesGroupDTO } from 'app/communication/shared/entities/forwarded-message.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting, PostingType } from 'app/communication/shared/entities/posting.model';
import { canCreateNewMessageInConversation } from 'app/communication/conversations/conversation-permissions.utils';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { getIsMobileSignal } from 'app/foundation/util/global.utils';

interface PostGroup {
    author: User | undefined;
    posts: Post[];
}

/**
 * Returns a new {@link Post} reference differing only in `isConsecutive`, so grouping can flag a post without touching
 * the cached one. Built with {@link Post.withSameValues} rather than a copy: this runs for every post in the channel on
 * every regroup (new message, reaction, edit, load-more), and the author, reactions and answers must keep their identity
 * so the rendered children are not re-created.
 */
function withConsecutiveFlag(post: Post, isConsecutive: boolean): Post {
    const flagged = Post.withSameValues(post);
    flagged.isConsecutive = isConsecutive;
    return flagged;
}

// sub-pixel scroll positions on scaled displays must still count as "at the bottom"
const SCROLLED_TO_BOTTOM_TOLERANCE_PX = 5;

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
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy {
    private sessionStorageService = inject(SessionStorageService);
    private breakpointObserver = inject(BreakpointObserver);
    communicationService = inject(CommunicationService);
    courseConversationsService = inject(CourseConversationsService);
    accountService = inject(AccountService);

    private ngUnsubscribe = new Subject<void>();
    readonly isMobile = getIsMobileSignal(this.breakpointObserver);
    readonly sessionStorageKey = 'conversationId.scrollPosition.';

    readonly PageType = PageType;
    readonly ButtonType = ButtonType;
    readonly ButtonSize = ButtonSize;

    private scrollDebounceTime = 100; // ms
    scrollSubject = new Subject<number>();
    canStartSaving = false;
    createdNewMessage = false;

    readonly openThread = output<Post>();

    readonly messages = viewChildren<PostingThreadComponent>('postingThread');
    // Intentionally NOT `viewChild.required`: `#container` lives inside the template's `@if (course())`,
    // and the effects created in the constructor run before view queries resolve (see the note above them).
    // A required query throws NG0951 in both cases, so this must stay optional and every read guarded.
    readonly content = viewChild<ElementRef>('container');
    readonly messageListContent = viewChild<ElementRef>('messageListContent');
    private resizeObserver?: ResizeObserver;
    private bottomScrollFrame: number | undefined;
    private pendingPageScrollDistance: number | undefined;
    private preserveScrollOnNextPage = false;
    private postRequestId = 0;
    private postPreparationId = 0;
    private readonly isPreparingPosts = signal(false);
    private restoringPageScroll = false;
    private pageScrollFrame: number | undefined;

    readonly course = input<Course>();
    showOnlyPinned = input<boolean>(false);
    pinnedCount = output<number>();
    readonly pinnedPosts = signal<Post[]>([]);

    readonly focusPostId = input<number | undefined>(undefined);
    readonly openThreadOnFocus = input<boolean>(false);

    getAsChannel = getAsChannelDTO;

    canCreateNewMessageInConversation = canCreateNewMessageInConversation;

    // whether the user was scrolled to the bottom of the message list before the latest posts were applied
    private wasScrolledToBottom = false;
    currentPostContextFilter?: PostContextFilter;
    private readonly search$ = new Subject<string>();
    searchText = '';
    readonly _activeConversation = signal<ConversationDTO | undefined>(undefined);
    readonly onNavigateToPost = output<Posting>();

    elementsAtScrollPosition: PostingThreadComponent[] = [];
    readonly newPost = signal<Post | undefined>(undefined);
    readonly posts = signal<Post[]>([]);
    readonly allPosts = signal<Post[]>([]);
    unreadPosts: Post[] = [];
    readonly groupedPosts = signal<PostGroup[]>([]);
    readonly totalNumberOfPosts = signal(0);
    page = 1;
    readonly isFetchingPosts = signal(true);
    currentUser!: User; // set in ngOnInit() from the resolved account identity
    readonly firstUnreadPostId = signal<number | undefined>(undefined);
    readonly unreadPostsCount = signal<number>(0);
    readonly atNewPostPosition = signal(false);

    // Icons
    protected readonly faTimes = faTimes;
    protected readonly faEnvelope = faEnvelope;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faArrowDown = faArrowDown;

    readonly isHiddenInputWithCallToAction = signal(false);
    readonly isHiddenInputFull = signal(false);
    focusOnPostId: number | undefined = undefined;
    isOpenThreadOnFocus = false;

    constructor() {
        // Reviewed for the effect()-debt cleanup (P2.2) and intentionally kept as effects: the first three react to
        // rendered view children / focus inputs to perform DOM side effects (scroll, highlight, requestAnimationFrame)
        // — exactly what effect() is for. The last one re-runs the imperative setPosts() (which filters, reverses,
        // re-groups, and may fetch forwarded messages over HTTP, and is also driven from the posts subscription), so it
        // cannot be expressed as a computed().
        effect(() => {
            const focusPostIdValue = this.focusPostId();
            const openThreadOnFocusValue = this.openThreadOnFocus();
            untracked(() => {
                this.focusOnPostId = focusPostIdValue;
                this.isOpenThreadOnFocus = openThreadOnFocusValue;
                // When messages are already rendered (e.g. navigating to a post
                // in the current conversation via global search), scroll and
                // highlight immediately — the messages() effect won't re-fire.
                if (focusPostIdValue && this.messages().length > 0) {
                    requestAnimationFrame(() => this.goToLastSelectedElement(focusPostIdValue, openThreadOnFocusValue));
                }
            });
        });

        // viewChildren returns a signal; use effect() to react to changes.
        // These effects are created in the constructor (injection context) but
        // the viewChildren signals won't resolve until after view init, which
        // is fine because effects re-run reactively.
        afterRenderEffect(() => {
            void this.messages();
            void this.groupedPosts();
            const pending = this.isFetchingPosts() || this.isPreparingPosts();
            untracked(() => {
                if (!pending) this.handleScrollOnNewMessage();
            });
        });
        effect(() => {
            // Read the signal to track changes
            void this.messages();
            untracked(() => {
                // Once the initial position is restored, a user who is at the bottom stays there when a message arrives.
                // Restoring the stored top-visible post would otherwise override the scroll to the bottom above.
                // A navigation that is still pending, to a post that is not loaded yet, is not a stored position: it needs the
                // next page that arrives to reach its target, so it is not held back by the bottom-following guard.
                const hasPendingNavigation = this.focusOnPostId !== undefined;
                const keepsFollowingNewMessages = this.canStartSaving && this.wasScrolledToBottom && !hasPendingNavigation;
                const keepsSearchPosition = !!this.searchText && this.canStartSaving && !hasPendingNavigation;
                if (
                    !this.isFetchingPosts() &&
                    !this.isPreparingPosts() &&
                    this.pendingPageScrollDistance === undefined &&
                    !this.createdNewMessage &&
                    this.posts().length > 0 &&
                    !keepsFollowingNewMessages &&
                    !keepsSearchPosition &&
                    !this.restoringPageScroll
                ) {
                    this.scrollToStoredId();
                }
            });
        });
        effect(() => {
            // Track showOnlyPinned signal input (replaces ngOnChanges)
            this.showOnlyPinned();
            untracked(() => {
                if (this.initialized) {
                    this.setPosts();
                }
            });
        });
    }

    private initialized = false;

    /**
     * Applies pinned message filter based on current toggle state.
     * If the toggle is active, only pinned posts are shown; otherwise, all posts.
     */
    applyPinnedMessageFilter(): void {
        if (this.showOnlyPinned()) {
            this.posts.set(this.pinnedPosts());
        } else {
            this.posts.set([...this.allPosts()]);
        }
    }

    ngOnInit(): void {
        this.subscribeToSearch();
        this.subscribeToPosts();
        this.subscribeToActiveConversation();
        this.setupScrollDebounce();

        // Fetch and subscribe to pinned posts, emit count to parent component
        this.communicationService
            .getPinnedPosts()
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe((pinnedPosts) => {
                this.pinnedPosts.set(pinnedPosts);
                this.pinnedCount.emit(pinnedPosts.length);
            });

        void this.accountService.identity().then((user: User | undefined) => {
            this.currentUser = user!;
        });

        // Ensure that all pinned posts are fetched when the component is initialized
        this.communicationService.fetchAllPinnedPosts(this._activeConversation()!.id!).subscribe();
        this.initialized = true;
    }

    private subscribeToActiveConversation() {
        this.courseConversationsService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO | undefined) => {
            // This statement avoids a bug that reloads the messages when the conversation is already displayed
            if (conversation && this._activeConversation()?.id === conversation.id) {
                return;
            }
            this._activeConversation.set(conversation);
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

    private scrollListener = () => {
        if (!this.isFetchingPosts() && !this.restoringPageScroll) this.captureScrolledToBottom();
        this.findElementsAtScrollPosition();
    };
    private mutationObserver: MutationObserver | undefined;

    ngAfterViewInit() {
        const el = this.content()?.nativeElement;
        if (!el) {
            // No course set, so `@if (course())` kept the container out of the DOM; nothing to observe.
            return;
        }
        el.addEventListener('scroll', this.scrollListener);
        this.resizeObserver = new ResizeObserver(() => {
            if (
                this.wasScrolledToBottom &&
                this.canStartSaving &&
                !this.isFetchingPosts() &&
                this.pendingPageScrollDistance === undefined &&
                !this.restoringPageScroll &&
                !this.focusOnPostId
            ) {
                this.scrollToBottomOfMessages();
            }
        });
        this.resizeObserver.observe(el);
        const messageList = this.messageListContent()?.nativeElement;
        if (messageList) this.resizeObserver.observe(messageList);

        this.mutationObserver = new MutationObserver(() => {
            this.findElementsAtScrollPosition();
        });
        this.mutationObserver.observe(el, {
            childList: true,
            subtree: true,
        });
    }

    ngOnDestroy(): void {
        this.postRequestId++;
        this.postPreparationId++;
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.scrollSubject.complete();
        this.content()?.nativeElement.removeEventListener('scroll', this.scrollListener);
        this.mutationObserver?.disconnect();
        this.resizeObserver?.disconnect();
        if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
        if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
    }

    private scrollToStoredId() {
        let savedScrollId: number | undefined;
        if (this.focusOnPostId) {
            savedScrollId = this.focusOnPostId;
        } else {
            const activeConversationId = this._activeConversation()?.id;
            savedScrollId = activeConversationId ? this.sessionStorageService.retrieve<number>(this.sessionStorageKey + activeConversationId) : undefined;
        }
        if (savedScrollId && (!this.searchText || this.focusOnPostId !== undefined)) {
            requestAnimationFrame(() => this.goToLastSelectedElement(savedScrollId, this.isOpenThreadOnFocus));
        } else {
            void this.goToLastSelectedElement(0, false);
        }
    }

    private onActiveConversationChange() {
        const activeConversation = this._activeConversation();
        if (activeConversation !== undefined && this.getAsChannel(activeConversation)?.isAnnouncementChannel) {
            this.isHiddenInputFull.set(!canCreateNewMessageInConversation(activeConversation));
            this.isHiddenInputWithCallToAction.set(canCreateNewMessageInConversation(activeConversation));
        } else {
            this.isHiddenInputFull.set(false);
            this.isHiddenInputWithCallToAction.set(false);
        }

        if (this.course() && activeConversation) {
            if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
            this.bottomScrollFrame = undefined;
            this.postPreparationId++;
            this.isPreparingPosts.set(false);
            this.allPosts.set([]);
            this.posts.set([]);
            this.groupedPosts.set([]);
            this.computeLastReadState();
            this.canStartSaving = false;
            this.onSearch();
            // the scroll state of the previously opened conversation does not apply to the new one
            this.wasScrolledToBottom = false;
            this.createEmptyPost();
            this.communicationService.fetchAllPinnedPosts(activeConversation.id!).subscribe({
                next: (pinnedPosts: Post[]) => {
                    this.pinnedPosts.set(pinnedPosts);
                    this.pinnedCount.emit(pinnedPosts.length);
                },
            });
        }
    }

    private subscribeToPosts() {
        this.communicationService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.allPosts.set(posts);
            this.setPosts();
            this.computeLastReadState();
        });
        this.communicationService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts.set(totalNumberOfPosts);
        });
    }

    private refreshConversationPostContextFilter(): void {
        const activeConversationId = this._activeConversation()?.id;
        this.currentPostContextFilter = {
            courseId: this.course()?.id,
            conversationIds: activeConversationId ? [activeConversationId] : undefined,
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
    private groupPosts(preparationId = this.postPreparationId): void {
        if (preparationId !== this.postPreparationId) return;
        // Forwarded-source lookups leave the old groups visible; include reader movement during those lookups too.
        if (this.pendingPageScrollDistance !== undefined) {
            const element = this.content()?.nativeElement;
            this.pendingPageScrollDistance = element ? element.scrollHeight - element.scrollTop : undefined;
        }
        // If there are no posts, clear groupedPosts and exit.
        if (!this.posts() || this.posts().length === 0) {
            this.groupedPosts.set([]);
            this.isPreparingPosts.set(false);
            return;
        }

        // Sort posts by the creation date
        const sortedPosts = [...this.posts()].sort((a, b) => {
            return a.creationDate!.valueOf() - b.creationDate!.valueOf();
        });

        // Compute new grouping based on current posts.
        const computedGroups: PostGroup[] = [];
        let currentGroup: PostGroup | undefined = undefined;

        sortedPosts.forEach((post) => {
            if (!currentGroup) {
                // Start new group if none exists.
                currentGroup = { author: post.author, posts: [withConsecutiveFlag(post, false)] };
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
                currentGroup.posts.push(withConsecutiveFlag(post, true));
            } else {
                computedGroups.push(currentGroup);
                currentGroup = { author: post.author, posts: [withConsecutiveFlag(post, false)] };
            }
        });
        if (currentGroup) {
            computedGroups.push(currentGroup);
        }

        // Update existing groups in place if possible.
        if (this.groupedPosts().length === computedGroups.length) {
            computedGroups.forEach((g, i) => {
                if (this.groupedPosts()[i].author?.id === g.author?.id) {
                    // If the group belongs to the same author, update its posts array in place.
                    this.groupedPosts()[i].posts.splice(0, this.groupedPosts()[i].posts.length, ...g.posts);
                } else {
                    // If group identity has changed, replace the group.
                    this.groupedPosts()[i] = g;
                }
            });
            // Notify the signal after the in-place group updates so the list re-renders independently of sibling writes.
            this.groupedPosts.set([...this.groupedPosts()]);
        } else {
            this.groupedPosts.set(computedGroups);
        }
        this.isPreparingPosts.set(false);
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
        const preparationId = ++this.postPreparationId;
        this.isPreparingPosts.set(true);
        // Preserve the scroll state captured before the request until the new messages have been rendered.
        if (!this.isFetchingPosts()) {
            this.captureScrolledToBottom();
        }
        this.applyPinnedMessageFilter();
        this.reversePosts();

        const postIds = this.getPostIdsWithForwardedMessages();

        if (postIds.length === 0) {
            this.groupPosts(preparationId);
            return;
        }
        // only fetch forwarded messages if there are any
        this.fetchForwardedMessages(postIds, preparationId);
    }

    private fetchForwardedMessages = (postIds: number[], preparationId: number) => {
        const forwardedRequest = this.communicationService.getForwardedMessagesByIds(postIds, PostingType.POST);
        if (!forwardedRequest) {
            this.groupPosts(preparationId);
            return;
        }
        forwardedRequest
            .pipe(
                catchError(() => of(undefined)),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((response) => {
                if (preparationId !== this.postPreparationId) return;
                const forwardedMessagesGroups = response?.body;

                if (!forwardedMessagesGroups) {
                    this.groupPosts(preparationId);
                    return;
                }

                const forwardedMessagesMap = this.mapForwardedMessages(forwardedMessagesGroups);
                const { sourcePostIds, sourceAnswerIds } = this.collectSourceIds(forwardedMessagesMap);
                const requests = this.buildSourceRequests(sourcePostIds, sourceAnswerIds);

                if (requests.length === 0) {
                    this.groupPosts(preparationId);
                    return;
                }

                forkJoin(requests)
                    .pipe(takeUntil(this.ngUnsubscribe))
                    .subscribe((responses) => {
                        if (preparationId !== this.postPreparationId) return;
                        const { fetchedPosts, fetchedAnswerPosts } = this.extractFetchedSources(responses);
                        this.posts.set(this.attachForwardedMessages(this.posts(), forwardedMessagesMap, fetchedPosts, fetchedAnswerPosts));
                        this.groupPosts(preparationId);
                        if (this.createdNewMessage) {
                            this.scrollToBottomOfMessages();
                            this.createdNewMessage = false;
                        }
                    });
            });
    };

    /**
     * Remembers whether the user is at the bottom of the visible message list, so that a newly received message is scrolled into view only then.
     */
    private captureScrolledToBottom(): void {
        const content = this.content();
        if (content) {
            const { scrollHeight, scrollTop, clientHeight } = content.nativeElement;
            this.wasScrolledToBottom = scrollHeight - scrollTop - clientHeight <= SCROLLED_TO_BOTTOM_TOLERANCE_PX;
        }
    }

    /**
     * Reverses the posts array to maintain chronological order.
     */
    private reversePosts(): void {
        this.posts.set(this.posts().slice().reverse());
    }

    /**
     * Filters posts that contain forwarded messages and have a valid ID.
     */
    private getPostIdsWithForwardedMessages(): number[] {
        return this.posts()
            .filter((post) => post.hasForwardedMessages && post.id !== undefined)
            .map((post) => post.id!);
    }

    /**
     * Converts list of forwarded message groups to a map from post ID to message list.
     */
    private mapForwardedMessages(groups: ForwardedMessagesGroupDTO[]): Map<number, ForwardedMessageDTO[]> {
        return new Map(groups.map((group) => [group.id!, group.messages]));
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

        // forwarded-source previews are non-critical: a source may live in a conversation the viewer cannot access (403),
        // or the fetch may otherwise fail. Swallow any error to undefined so one bad source does not break rendering of the
        // whole message list (extractFetchedSources tolerates undefined).
        if (sourcePostIds.length > 0) {
            requests.push(this.communicationService.getSourcePostsByIds(sourcePostIds).pipe(catchError(() => of(undefined))));
        }

        if (sourceAnswerIds.length > 0) {
            requests.push(this.communicationService.getSourceAnswerPostsByIds(sourceAnswerIds).pipe(catchError(() => of(undefined))));
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
                    fetchedPosts = response;
                } else if ((first as AnswerPost).resolvesPost !== undefined) {
                    fetchedAnswerPosts = response;
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
        if (this.isFetchingPosts() || this.isPreparingPosts()) return;
        if (this.posts().length >= this.totalNumberOfPosts()) {
            this.canStartSaving = true;
            return;
        }
        // Navigation/restoration must keep looking for its target as pages arrive; only reader-driven pagination keeps a viewport anchor.
        this.preserveScrollOnNextPage = this.canStartSaving && this.focusOnPostId === undefined;
        const previousPage = this.page;
        this.page += 1;
        this.fetchPosts(false, previousPage);
    }

    public fetchPosts(forceUpdate = false, previousPage = this.page) {
        this.refreshConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            if (!this.isFetchingPosts()) {
                // Capture the position before the response changes the message list.
                this.captureScrolledToBottom();
            }
            this.isFetchingPosts.set(true);
            const requestId = ++this.postRequestId;
            this.communicationService.getFilteredPosts(this.currentPostContextFilter, forceUpdate, this._activeConversation(), {
                beforeApply: () => {
                    if (requestId !== this.postRequestId || !this.preserveScrollOnNextPage) return;
                    // Capture only for the requested response, after any reader movement and intervening live updates.
                    const element = this.content()?.nativeElement;
                    this.pendingPageScrollDistance = element ? element.scrollHeight - element.scrollTop : undefined;
                    this.preserveScrollOnNextPage = false;
                },
                complete: (applied) => {
                    if (requestId !== this.postRequestId) return;
                    this.isFetchingPosts.set(false);
                    this.preserveScrollOnNextPage = false;
                    if (!applied) {
                        this.page = previousPage;
                        this.pendingPageScrollDistance = undefined;
                    }
                    // Position only after grouped posts (including forwarded sources) have rendered.
                },
            });
        }
    }

    onSearch(): void {
        if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
        this.pageScrollFrame = undefined;
        this.restoringPageScroll = false;
        this.page = 1;
        this.canStartSaving = false;
        this.pendingPageScrollDistance = undefined;
        this.preserveScrollOnNextPage = false;
        this.fetchPosts(true);
    }

    createEmptyPost(): void {
        this.newPost.set(this.buildEmptyPostForActiveConversation());
    }

    private buildEmptyPostForActiveConversation() {
        const activeConversation = this._activeConversation();
        if (!activeConversation) {
            return undefined;
        }
        let conversation: Conversation;
        if (isChannelDTO(activeConversation)) {
            const channel = new Channel();
            channel.isAnnouncementChannel = activeConversation.isAnnouncementChannel;
            conversation = channel;
        } else if (isGroupChatDTO(activeConversation)) {
            conversation = new GroupChat();
        } else if (isOneToOneChatDTO(activeConversation)) {
            conversation = new OneToOneChat();
        } else {
            throw new Error('Conversation type not supported');
        }
        conversation.id = activeConversation.id;
        this.refreshConversationPostContextFilter();
        return this.communicationService.createEmptyPostForContext(conversation);
    }

    postsGroupTrackByFn = (_index: number, post: PostGroup): string => 'grp_' + post.posts.map((p) => p.id?.toString()).join('_');

    postsTrackByFn = (_index: number, post: Post): string => 'post_' + post.id!;

    setPostForThread(post: Post) {
        this.openThread.emit(post);
    }

    handleScrollOnNewMessage = () => {
        if (this.isFetchingPosts() || this.isPreparingPosts()) return;
        const el = this.content()?.nativeElement;
        if (!el) {
            return;
        }
        if (this.pendingPageScrollDistance !== undefined) {
            const distance = this.pendingPageScrollDistance;
            this.pendingPageScrollDistance = undefined;
            this.restoringPageScroll = true;
            this.pageScrollFrame = requestAnimationFrame(() => {
                this.pageScrollFrame = undefined;
                const element = this.content()?.nativeElement;
                if (element) element.scrollTop = element.scrollHeight - distance;
                this.restoringPageScroll = false;
            });
            return;
        }
        // Before the initial position is restored, a list at the very top of the first page is positioned at the bottom.
        // Afterwards, a user who scrolled up to the oldest messages must stay in place.
        const isInitialPositioning = !this.canStartSaving && this.posts().length > 0 && el.scrollTop === 0 && this.page === 1;
        if (isInitialPositioning || this.wasScrolledToBottom) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
        this.bottomScrollFrame = requestAnimationFrame(() => {
            this.bottomScrollFrame = undefined;
            const el = this.content()?.nativeElement;
            if (el) {
                el.scrollTop = el.scrollHeight;
                this.wasScrolledToBottom = true;
            }
        });
    }

    private setupScrollDebounce(): void {
        this.scrollSubject.pipe(debounceTime(this.scrollDebounceTime), takeUntil(this.ngUnsubscribe)).subscribe((postId) => {
            const activeConversationId = this._activeConversation()?.id;
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
        const messageArray = this.messages();
        const element = messageArray.find((message) => message.post().id === lastScrollPosition); // Suchen nach dem Post

        if (!element) {
            this.fetchNextPage();
        } else {
            // We scroll to the element with a slight buffer to ensure its fully visible (-10)
            const containerEl = this.content()?.nativeElement;
            if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
            this.bottomScrollFrame = undefined;
            if (containerEl) {
                containerEl.scrollTop = Math.max(0, element.elementRef.nativeElement.offsetTop - 10);
                // A ResizeObserver can run before the native scroll event. Historical navigation must stop bottom-following synchronously.
                this.captureScrolledToBottom();
            }
            this.canStartSaving = true;
            if (isOpenThread) {
                this.openThread.emit(element.post());
            }
            if (this.focusOnPostId) {
                const postDiv = element.elementRef.nativeElement.querySelector('.post');
                if (postDiv) {
                    postDiv.classList.add('highlight-post');
                    setTimeout(() => postDiv.classList.remove('highlight-post'), 2000);
                }
            }
            this.focusOnPostId = undefined;
            this.isOpenThreadOnFocus = false;
        }
    }

    findElementsAtScrollPosition() {
        const messageArray = this.messages();
        const containerEl = this.content()?.nativeElement;
        if (!containerEl) {
            return;
        }
        const containerRect = containerEl.getBoundingClientRect();
        const visibleMessages = [];
        for (const message of messageArray) {
            if (!message.elementRef?.nativeElement || !message.post()?.id) continue;
            const rect = message.elementRef.nativeElement.getBoundingClientRect();
            if (rect.top >= containerRect.top && rect.bottom <= containerRect.bottom) {
                visibleMessages.push(message);
                break; // Only need the first visible message
            }
        }
        this.elementsAtScrollPosition = visibleMessages;
        if (this.canStartSaving) {
            const { scrollHeight, scrollTop, clientHeight } = containerEl;
            if (scrollHeight - scrollTop - clientHeight <= SCROLLED_TO_BOTTOM_TOLERANCE_PX) {
                // Zero means "follow the newest messages" when reopening, independently of the composer/viewport height.
                this.saveScrollPosition(0);
            } else if (visibleMessages.length > 0) {
                this.saveScrollPosition(visibleMessages[0].post().id!);
            }
        }
        this.setFirstUnreadPostId();
        this.atNewPostPosition.set(this.isAnyUnreadPostVisible());
    }

    /**
     * Emits navigation event to a specific post (used by forwarded post component).
     */
    onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }

    private computeLastReadState(): void {
        this.unreadPosts = this.getUnreadPosts();
        this.unreadPostsCount.set(this.unreadPosts.length);
        this.setFirstUnreadPostId();
    }

    /**
     * Returns a list of posts that were created after the lastReadDate
     * and were not authored by the current user (i.e. true unread posts).
     */
    private getUnreadPosts(): Post[] {
        const lastReadDate = this._activeConversation()?.lastReadDate;
        if (!lastReadDate || !this.allPosts()?.length) {
            return [];
        }

        return getUnreadPostsByLastReadDate(this.currentUser, this.allPosts(), lastReadDate);
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

        const component = this.messages().find((m) => m.post().id === this.firstUnreadPostId());
        if (!component?.elementRef?.nativeElement) {
            return;
        }

        const containerElement = this.content()?.nativeElement;
        if (!containerElement) {
            return;
        }
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
        const content = this.content();
        if (!content?.nativeElement) {
            return undefined;
        }

        const component = this.messages().find((m) => m.post().id === postId);
        if (!component?.elementRef?.nativeElement) {
            return undefined;
        }

        const postRect = component.elementRef.nativeElement.getBoundingClientRect();
        const containerRect = content.nativeElement.getBoundingClientRect();

        return { postRect, containerRect };
    }

    /**
     * Returns the bounding rectangles of the first unread post and the scroll container.
     * Returns `undefined` if the post or container is not available in the DOM.
     */
    private getBoundingRectsForFirstUnreadPost(): { postRect: DOMRect; containerRect: DOMRect } | undefined {
        if (!this.unreadPosts.length || !this.firstUnreadPostId()) {
            return undefined;
        }

        return this.getBoundingRectsForPost(this.firstUnreadPostId()!);
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
            this.firstUnreadPostId.set(this.unreadPosts[0].id);
        } else {
            this.firstUnreadPostId.set(undefined);
        }
    }
}

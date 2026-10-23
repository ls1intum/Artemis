import { PostPageRequestState } from 'app/communication/course-conversations-components/post-page-request-state';
import { BreakpointObserver } from '@angular/cdk/layout';
import {
    AfterViewInit,
    Component,
    ElementRef,
    OnDestroy,
    OnInit,
    ViewEncapsulation,
    afterRenderEffect,
    computed,
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
import { PageType, PostContextFilter, PostSortCriterion, SortDirection, USER_SCROLL_EVENTS, getUnreadPostsByLastReadDate } from 'app/communication/communication.util';
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
// space kept above the new-messages line when the list opens at it, so that the line is not flush with the edge
const NEW_MESSAGES_LINE_MARGIN_PX = 8;

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
    private readonly postPageRequest = new PostPageRequestState();
    private postPreparationId = 0;
    private readonly isPreparingPosts = signal(false);
    private restoringPageScroll = false;
    private pageScrollFrame: number | undefined;
    // The post the list opened at. Images and link previews above it load after it was positioned and push it out of view,
    // so its position is held until the user scrolls themselves.
    private initialAnchor: { postId: number; alignToUnreadLine: boolean } | undefined;
    /** Release the opening unread anchor on reader input so later rendering cannot pull the reader back. */
    private readonly releaseInitialAnchor = () => {
        this.initialAnchor = undefined;
    };

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
    // Keep the opening divider stable while successful reads advance the shared conversation cache.
    private readonly openingLastReadDate = computed(() => this._activeConversation()?.lastReadDate);
    readonly onNavigateToPost = output<Posting>();
    readonly forwardedMessagesLoaded = output<Post[]>();

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
                    this.postPageRequest.pendingScrollDistance === undefined &&
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
            // Capture before loading posts can update the same DTO's lastReadDate.
            this.openingLastReadDate();
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

    /** Observe settled layout while holding the initial unread anchor, and release it on explicit reader input. */
    ngAfterViewInit() {
        const el = this.content()?.nativeElement;
        if (!el) {
            // No course set, so `@if (course())` kept the container out of the DOM; nothing to observe.
            return;
        }
        el.addEventListener('scroll', this.scrollListener);
        this.resizeObserver = new ResizeObserver(() => {
            const isPositioned =
                this.canStartSaving && !this.isFetchingPosts() && this.postPageRequest.pendingScrollDistance === undefined && !this.restoringPageScroll && !this.focusOnPostId;
            if (isPositioned && this.initialAnchor) {
                // Held before following the bottom: the list may still have been short when the post was put at the top, so that its
                // position was clamped to the end of the list, which counts as being at the bottom although the user never went there.
                this.reapplyInitialAnchor();
            } else if (isPositioned && this.wasScrolledToBottom) {
                this.scrollToBottomOfMessages();
            }
        });
        USER_SCROLL_EVENTS.forEach((eventName) => el.addEventListener(eventName, this.releaseInitialAnchor, { passive: true }));
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

    /** Invalidate pending rendering and remove scroll listeners and layout observers owned by this message view. */
    ngOnDestroy(): void {
        this.postPageRequest.invalidate();
        this.postPreparationId++;
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.scrollSubject.complete();
        this.content()?.nativeElement.removeEventListener('scroll', this.scrollListener);
        USER_SCROLL_EVENTS.forEach((eventName) => this.content()?.nativeElement.removeEventListener(eventName, this.releaseInitialAnchor));
        this.mutationObserver?.disconnect();
        this.resizeObserver?.disconnect();
        if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
        if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
    }

    /**
     * Prioritizes explicit post navigation, then the first unread message on opening. Fully read conversations
     * open at the newest message; saved positions preserve an active reader or an unloaded first-unread target.
     */
    private scrollToStoredId() {
        let savedScrollId: number | undefined;
        if (this.focusOnPostId) {
            savedScrollId = this.focusOnPostId;
        } else {
            const firstUnreadPostId = this.firstUnreadPostIdToOpenAt();
            if (firstUnreadPostId !== undefined) {
                requestAnimationFrame(() => this.goToLastSelectedElement(firstUnreadPostId, false, true));
                return;
            }
            if (!this.canStartSaving && !this.searchText && this.unreadPostsCount() === 0) {
                void this.goToLastSelectedElement(0, false);
                return;
            }
            const activeConversationId = this._activeConversation()?.id;
            savedScrollId = activeConversationId ? this.sessionStorageService.retrieve<number>(this.sessionStorageKey + activeConversationId) : undefined;
        }
        if (savedScrollId && (!this.searchText || this.focusOnPostId !== undefined)) {
            // A reader at the new-messages line keeps the line, not the post below it, at the top when a message arrives.
            const isAtNewMessagesLine = this.focusOnPostId === undefined && savedScrollId === this.firstUnreadPostId();
            requestAnimationFrame(() =>
                isAtNewMessagesLine
                    ? this.goToLastSelectedElement(savedScrollId, this.isOpenThreadOnFocus, true)
                    : this.goToLastSelectedElement(savedScrollId, this.isOpenThreadOnFocus),
            );
        } else {
            void this.goToLastSelectedElement(0, false);
        }
    }

    /**
     * The first unread post when the list should open at it, instead of at the stored position or the bottom.
     * <p>
     * Only the initial positioning does so: afterwards the position the reader is at is the one to keep, and a message
     * that arrives must not pull them back to the first unread one. It also needs the first unread post to really be the
     * first one: either an older, read post is loaded, or nothing older exists. With more unread messages than the
     * loaded page holds, the first unread message is on an older page, and the oldest loaded post must not be presented
     * as the start of the unread ones.
     */
    private firstUnreadPostIdToOpenAt(): number | undefined {
        const firstUnreadPostId = this.firstUnreadPostId();
        const lastReadDate = this.displayedLastReadDate();
        if (this.canStartSaving || this.searchText || firstUnreadPostId === undefined || !lastReadDate) {
            return undefined;
        }
        const allPosts = this.allPosts();
        const isEverythingLoaded = allPosts.length >= this.totalNumberOfPosts();
        const hasLoadedReadPost = allPosts.some((post) => !post.creationDate?.isAfter(lastReadDate));
        return isEverythingLoaded || hasLoadedReadPost ? firstUnreadPostId : undefined;
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
            this.initialAnchor = undefined;
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
        if (this.postPageRequest.pendingScrollDistance !== undefined) {
            this.postPageRequest.captureScrollDistance(this.content()?.nativeElement);
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
                        this.forwardedMessagesLoaded.emit(this.posts());
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

        // A source may have been deleted or its preview may fail to load. Keep the rest of the chat usable;
        // the forwarded-message component shows an inline fallback for unavailable sources.
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
                if ((first as Post).conversation !== undefined || (first as Post).resolved !== undefined) {
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
        this.postPageRequest.preserveScrollOnNextPage = this.canStartSaving && this.focusOnPostId === undefined;
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
            this.communicationService.getFilteredPosts(
                this.currentPostContextFilter,
                forceUpdate,
                this._activeConversation(),
                this.postPageRequest.callbacks(
                    () => this.content()?.nativeElement,
                    (applied) => {
                        this.isFetchingPosts.set(false);
                        if (!applied) {
                            this.page = previousPage;
                        }
                        // Position only after grouped posts (including forwarded sources) have rendered.
                    },
                ),
            );
        }
    }

    onSearch(): void {
        if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
        this.pageScrollFrame = undefined;
        this.restoringPageScroll = false;
        this.page = 1;
        this.canStartSaving = false;
        this.postPageRequest.pendingScrollDistance = undefined;
        this.postPageRequest.preserveScrollOnNextPage = false;
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
        if (this.postPageRequest.pendingScrollDistance !== undefined) {
            const distance = this.postPageRequest.pendingScrollDistance;
            this.postPageRequest.pendingScrollDistance = undefined;
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
        this.initialAnchor = undefined;
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

    /**
     * Scrolls to a post, or to the bottom for the position 0, and holds that position while the content around it settles.
     *
     * @param lastScrollPosition the id of the post to scroll to, or 0 for the bottom
     * @param isOpenThread whether to open the thread of the post
     * @param alignToUnreadLine whether to put the new-messages line in front of the post at the top, instead of the post itself
     */
    async goToLastSelectedElement(lastScrollPosition: number, isOpenThread: boolean, alignToUnreadLine = false) {
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
            const containerEl = this.content()?.nativeElement;
            if (this.bottomScrollFrame !== undefined) window.cancelAnimationFrame(this.bottomScrollFrame);
            this.bottomScrollFrame = undefined;
            if (containerEl) {
                this.alignToPost(containerEl, element.elementRef.nativeElement, alignToUnreadLine);
                this.initialAnchor = { postId: lastScrollPosition, alignToUnreadLine };
                // The debounced saving would leave the old stored position in place for a moment, and a message arriving in that
                // moment would restore it, pulling the list away from the line it was just opened at.
                const activeConversationId = this._activeConversation()?.id;
                if (alignToUnreadLine && activeConversationId) {
                    this.sessionStorageService.store<number>(this.sessionStorageKey + activeConversationId, lastScrollPosition);
                }
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

    /**
     * Puts a post at the top of the list with a slight buffer to ensure it is fully visible (-10), or the new-messages line
     * in front of it, when that is asked for and rendered, with a smaller buffer.
     */
    private alignToPost(containerEl: HTMLElement, postElement: HTMLElement, alignToUnreadLine: boolean) {
        const unreadLine = alignToUnreadLine ? this.newMessagesLineOf(postElement) : undefined;
        containerEl.scrollTop = unreadLine ? Math.max(0, unreadLine.offsetTop - NEW_MESSAGES_LINE_MARGIN_PX) : Math.max(0, postElement.offsetTop - 10);
    }

    // the line sits in front of the post item that holds the post, see the template
    /** Find the separator immediately before a post when aligning the first unread message. */
    private newMessagesLineOf(postElement: HTMLElement): HTMLElement | undefined {
        const line = postElement.closest('.post-item')?.previousElementSibling;
        return line?.classList.contains('new-messages-separator') ? (line as HTMLElement) : undefined;
    }

    /** Restore the opening post or separator after layout changes while the reader still owns no scroll position. */
    private reapplyInitialAnchor() {
        const anchor = this.initialAnchor;
        const containerEl = this.content()?.nativeElement;
        const element = anchor ? this.messages().find((message) => message.post().id === anchor.postId) : undefined;
        if (anchor && containerEl && element) {
            this.alignToPost(containerEl, element.elementRef.nativeElement, anchor.alignToUnreadLine);
            this.captureScrolledToBottom();
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
        const lastReadDate = this.displayedLastReadDate();
        if (!lastReadDate || !this.allPosts()?.length) {
            return [];
        }

        return getUnreadPostsByLastReadDate(this.currentUser, this.allPosts(), lastReadDate);
    }

    /** Keep the opening divider stable through automatic reads, but honor a new explicit unread boundary. */
    private displayedLastReadDate() {
        const conversation = this._activeConversation();
        return this.courseConversationsService.isConversationMarkedAsUnread(conversation?.id) ? conversation?.lastReadDate : this.openingLastReadDate();
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

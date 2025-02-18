import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    ElementRef,
    EventEmitter,
    Input,
    OnDestroy,
    OnInit,
    Output,
    QueryList,
    ViewChild,
    ViewChildren,
    ViewEncapsulation,
    effect,
    inject,
    input,
} from '@angular/core';
import { faCircleNotch, faEnvelope, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Subject, map, takeUntil } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { DisplayPriority, PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { Channel, getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { ButtonType } from 'app/shared/components/button.component';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { OneToOneChat, isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { canCreateNewMessageInConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';
import dayjs from 'dayjs/esm';
import { User } from 'app/core/user/user.model';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { NgClass } from '@angular/common';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { MessageInlineInputComponent } from 'app/shared/metis/message/message-inline-input/message-inline-input.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy {
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

    elementsAtScrollPosition: PostingThreadComponent[];
    newPost?: Post;
    posts: Post[] = [];
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
        }
    }

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.setPosts(posts);
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
        // If there are no posts, clear groupedPosts and exit.
        if (!this.posts || this.posts.length === 0) {
            this.groupedPosts = [];
            return;
        }

        // Separate pinned posts from the rest.
        const pinnedPosts = this.posts.filter((post) => post.displayPriority === DisplayPriority.PINNED);
        const unpinnedPosts = this.posts.filter((post) => post.displayPriority !== DisplayPriority.PINNED);

        // Sort unpinned posts ascending by creation date.
        const sortedPosts = unpinnedPosts.sort((a, b) => {
            const aDate = (a as any).creationDateDayjs;
            const bDate = (b as any).creationDateDayjs;
            return aDate?.valueOf() - bDate?.valueOf();
        });

        const updatedGroups: PostGroup[] = [];
        let currentGroup: PostGroup | null = null;

        // Group posts that are by the same author and have less than 5 minutes difference.
        sortedPosts.forEach((post) => {
            if (!currentGroup) {
                // Start new group if none exists.
                currentGroup = { author: post.author, posts: [{ ...post, isConsecutive: false }] };
            } else {
                const lastPost = currentGroup.posts[currentGroup.posts.length - 1];
                const currentDate = (post as any).creationDateDayjs;
                const lastDate = (lastPost as any).creationDateDayjs;
                const timeDiff = currentDate && lastDate ? currentDate.diff(lastDate, 'minute') : Number.MAX_SAFE_INTEGER;
                // Check if current post should be added to the current group.
                if (this.isAuthorEqual(currentGroup, { author: post.author, posts: [] }) && timeDiff < 5 && timeDiff >= 0) {
                    currentGroup.posts.push({ ...post, isConsecutive: true });
                } else {
                    // Finalize current group and start a new one.
                    updatedGroups.push(currentGroup);
                    currentGroup = { author: post.author, posts: [{ ...post, isConsecutive: false }] };
                }
            }
        });
        // Add the final group if exists.
        if (currentGroup) {
            updatedGroups.push(currentGroup);
        }

        // Prepend pinned posts as a separate group if any exist.
        if (pinnedPosts.length) {
            updatedGroups.unshift({ author: undefined, posts: pinnedPosts });
        }

        // Update the groupedPosts property and trigger change detection.
        this.groupedPosts = updatedGroups;
        this.cdr.detectChanges();
    }

    private isAuthorEqual(groupA: PostGroup, groupB: PostGroup): boolean {
        // Both groups are equal if neither has an author; otherwise, they are not
        if (!groupA.author || !groupB.author) {
            return !groupA.author && !groupB.author;
        }
        return groupA.author.id === groupB.author.id;
    }

    setPosts(posts: Post[]): void {
        if (this.content) {
            this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        }

        this.posts = posts
            .slice()
            .reverse()
            .map((post) => {
                (post as any).creationDateDayjs = post.creationDate ? dayjs(post.creationDate) : undefined;
                return post;
            });

        // Incrementally update the grouped posts.
        this.groupPosts();
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
}

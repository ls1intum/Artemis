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
    Renderer2,
    ViewChild,
    ViewChildren,
    ViewEncapsulation,
    inject,
} from '@angular/core';
import { faCircleNotch, faEnvelope, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Subject, map, takeUntil } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
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

interface PostGroup {
    author: User | undefined;
    posts: Post[];
}

@Component({
    selector: 'jhi-conversation-messages',
    templateUrl: './conversation-messages.component.html',
    styleUrls: ['./conversation-messages.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    readonly sessionStorageKey = 'conversationId.scrollPosition.';

    readonly PageType = PageType;
    readonly ButtonType = ButtonType;

    private scrollDebounceTime = 100; // ms
    scrollSubject = new Subject<number>();
    canStartSaving = false;
    createdNewMessage = false;

    @Output() openThread = new EventEmitter<Post>();

    @ViewChild('searchInput')
    searchInput: ElementRef;

    @ViewChildren('postingThread')
    messages: QueryList<PostingThreadComponent>;
    @ViewChild('container')
    content: ElementRef;
    @Input()
    course?: Course;
    @Input()
    searchbarCollapsed = false;
    @Input()
    contentHeightDev: boolean = false;

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

    private layoutService: LayoutService = inject(LayoutService);
    private renderer = inject(Renderer2);

    constructor(
        public metisService: MetisService, // instance from course-conversations.component
        public metisConversationService: MetisConversationService, // instance from course-conversations.component
        public cdr: ChangeDetectorRef,
    ) {}

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
            if (this._activeConversation?.id === conversation.id) {
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
        const savedScrollId = sessionStorage.getItem(this.sessionStorageKey + this._activeConversation?.id) ?? '';
        requestAnimationFrame(() => this.goToLastSelectedElement(parseInt(savedScrollId, 10)));
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
        if (!this.posts || this.posts.length === 0) {
            this.groupedPosts = [];
            return;
        }

        const sortedPosts = this.posts.sort((a, b) => {
            const aDate = (a as any).creationDateDayjs;
            const bDate = (b as any).creationDateDayjs;
            return aDate?.valueOf() - bDate?.valueOf();
        });

        const groups: PostGroup[] = [];
        let currentGroup: PostGroup = {
            author: sortedPosts[0].author,
            posts: [{ ...sortedPosts[0], isConsecutive: false }],
        };

        for (let i = 1; i < sortedPosts.length; i++) {
            const currentPost = sortedPosts[i];
            const lastPostInGroup = currentGroup.posts[currentGroup.posts.length - 1];

            const currentDate = (currentPost as any).creationDateDayjs;
            const lastDate = (lastPostInGroup as any).creationDateDayjs;

            let timeDiff = Number.MAX_SAFE_INTEGER;
            if (currentDate && lastDate) {
                timeDiff = currentDate.diff(lastDate, 'minute');
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

    postsGroupTrackByFn = (index: number, post: PostGroup): string => 'grp_' + post.posts.map((p) => p.id?.toString()).join('_');

    postsTrackByFn = (index: number, post: Post): string => 'post_' + post.id!;

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

    async goToLastSelectedElement(lastScrollPosition: number) {
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
        if (this.elementsAtScrollPosition && this.canStartSaving) {
            this.saveScrollPosition(this.elementsAtScrollPosition[0].post.id!);
        }
    }
}

import { AfterViewInit, Component, ElementRef, EventEmitter, OnDestroy, OnInit, Output, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { faCircleNotch, faEnvelope, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Subject, Subscription, takeUntil } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { Channel, getAsChannel, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { ButtonType } from 'app/shared/components/button.component';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { OneToOneChat, isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';

@Component({
    selector: 'jhi-conversation-messages',
    templateUrl: './conversation-messages.component.html',
    styleUrls: ['./conversation-messages.component.scss'],
})
export class ConversationMessagesComponent implements OnInit, AfterViewInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    readonly PageType = PageType;
    readonly ButtonType = ButtonType;

    @Output() openThread = new EventEmitter<Post>();

    @ViewChildren('postingThread')
    messages: QueryList<any>;
    @ViewChild('container')
    content: ElementRef;
    course?: Course;

    getAsChannel = getAsChannel;

    previousScrollDistanceFromTop: number;
    // as set for the css class '.posting-infinite-scroll-container'
    messagesContainerHeight = 350;

    private scrollBottomSubscription: Subscription;
    private postInThread: Post;

    currentPostContextFilter?: PostContextFilter;
    searchText?: string;
    _activeConversation?: ConversationDto;

    newPost?: Post;
    posts: Post[] = [];
    totalNumberOfPosts = 0;
    page = 1;

    public isFetchingPosts = true;

    // subscriptions
    metisPostsSubscription: Subscription;
    metisTotalNumberOfPostsSubscription: Subscription;

    // Icons
    faTimes = faTimes;
    faSearch = faSearch;
    faEnvelope = faEnvelope;
    faCircleNotch = faCircleNotch;

    constructor(
        protected metisService: MetisService, // instance from course-messages.component
        public metisConversationService: MetisConversationService, // instance from course-messages.component
    ) {}

    ngOnInit(): void {
        this.course = this.metisConversationService.course!;
        this.setupMetis();
        this.subscribeToMetis();
        this.subscribeToActiveConversation();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDto) => {
            this._activeConversation = conversation;
            this.onActiveConversationChange();
        });
    }

    ngAfterViewInit() {
        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private onActiveConversationChange() {
        if (this.course && this._activeConversation) {
            this.searchText = '';
            this.commandMetisToFetchPosts(true);
            this.createEmptyPost();
        }
    }

    private setupMetis() {
        this.metisService.setPageType(PageType.OVERVIEW);
        this.metisService.setCourse(this.course!);
    }

    private subscribeToMetis() {
        this.metisPostsSubscription = this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.setPosts(posts);
            this.isFetchingPosts = false;
        });
        this.metisTotalNumberOfPostsSubscription = this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
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

    setPosts(posts: Post[]): void {
        this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        this.posts = posts.slice().reverse();
        if (this.postInThread) {
            this.setPostForThread(posts.find((post) => post.id === this.postInThread?.id)!);
        }
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        this.refreshMetisConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts = true; // will be set to false in subscription
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate);
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
        if (isChannelDto(this._activeConversation)) {
            conversation = new Channel();
        } else if (isGroupChatDto(this._activeConversation)) {
            conversation = new GroupChat();
        } else if (isOneToOneChatDto(this._activeConversation)) {
            conversation = new OneToOneChat();
        } else {
            throw new Error('Conversation type not supported');
        }
        conversation.id = this._activeConversation.id;
        this.refreshMetisConversationPostContextFilter();
        return this.metisService.createEmptyPostForContext(undefined, undefined, undefined, undefined, conversation);
    }

    postsTrackByFn = (index: number, post: Post): number => post.id!;

    setPostForThread(post: Post) {
        this.postInThread = post;
        this.openThread.emit(post);
    }
    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
    }
}

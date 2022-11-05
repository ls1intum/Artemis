import { AfterViewInit, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, QueryList, ViewChild, ViewChildren } from '@angular/core';
import { faCircleNotch, faEnvelope, faTimes, faSearch } from '@fortawesome/free-solid-svg-icons';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { map, of, Subscription, switchMap, take } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { Course } from 'app/entities/course.model';
import { PageType, PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { Channel, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, isGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { ActivatedRoute } from '@angular/router';
import { ButtonType } from 'app/shared/components/button.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-messages',
    templateUrl: './messages.component.html',
    styleUrls: ['./messages.component.scss'],
})
export class MessagesComponent implements OnInit, AfterViewInit, OnDestroy {
    readonly PageType = PageType;
    readonly ButtonType = ButtonType;

    @Output() openThread = new EventEmitter<Post>();

    @ViewChildren('postingThread')
    messages: QueryList<any>;
    @ViewChild('container')
    content: ElementRef;

    @Input() set activeConversation(newActiveConversation: ConversationDto) {
        if (!this._activeConversation || this._activeConversation.id !== newActiveConversation.id) {
            this._activeConversation = newActiveConversation;
            this.onActiveConversationChange();
        }
    }

    previousScrollDistanceFromTop: number;
    // as set for the css class '.posting-infinite-scroll-container'
    messagesContainerHeight = 350;

    private scrollBottomSubscription: Subscription;
    private postInThread: Post;
    course?: Course;
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
        private activatedRoute: ActivatedRoute,
        protected metisService: MetisService, // instance from course-messages.component
        private courseManagementService: CourseManagementService,
    ) {}

    ngOnInit(): void {
        this.activatedRoute
            .parent!.parent!.paramMap.pipe(
                take(1),
                switchMap((params) => {
                    const courseId = Number(params.get('courseId'));
                    // we need to load lecture and exercises too so that metis service can use it for references in post!
                    return this.courseManagementService.findOneForDashboard(courseId).pipe(map((res) => res.body!));
                }),
            )
            .subscribe((course: Course) => {
                if (course) {
                    this.course = course;
                    this.setupMetis();
                    this.subscribeToMetis();
                    if (this._activeConversation) {
                        this.onActiveConversationChange();
                    }
                }
            });
    }

    ngAfterViewInit() {
        this.scrollBottomSubscription = this.messages.changes.subscribe(this.handleScrollOnNewMessage);
    }

    ngOnDestroy(): void {
        this.metisPostsSubscription?.unsubscribe();
        this.metisTotalNumberOfPostsSubscription?.unsubscribe();
        this.scrollBottomSubscription?.unsubscribe();
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
        this.metisPostsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.setPosts(posts);
            this.isFetchingPosts = false;
        });
        this.metisTotalNumberOfPostsSubscription = this.metisService.totalNumberOfPosts.subscribe((totalNumberOfPosts: number) => {
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

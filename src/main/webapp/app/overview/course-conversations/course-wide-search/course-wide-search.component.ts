import { ChangeDetectorRef, Component, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, ViewChild, ViewEncapsulation } from '@angular/core';
import { faCircleNotch, faEnvelope, faTimes } from '@fortawesome/free-solid-svg-icons';
import { Subject, takeUntil } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';

@Component({
    selector: 'jhi-course-wide-search',
    templateUrl: './course-wide-search.component.html',
    styleUrls: ['./course-wide-search.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class CourseWideSearchComponent implements OnInit, OnDestroy {
    @Input()
    searchTerm?: string;

    @ViewChild('container')
    content: ElementRef;

    @Output() openThread = new EventEmitter<Post>();

    course: Course;

    currentPostContextFilter?: PostContextFilter;

    faTimes = faTimes;
    faEnvelope = faEnvelope;
    faCircleNotch = faCircleNotch;

    private readonly search$ = new Subject<string>();
    private ngUnsubscribe = new Subject<void>();
    public isFetchingPosts = true;
    totalNumberOfPosts = 0;
    posts: Post[] = [];
    previousScrollDistanceFromTop: number;
    page = 1;

    constructor(
        public metisService: MetisService, // instance from course-conversations.component
        public metisConversationService: MetisConversationService, // instance from course-conversations.component
        public cdr: ChangeDetectorRef,
    ) {}

    ngOnInit(): void {
        this.subscribeToMetis();
        this.cdr.detectChanges();
        this.onSearch(this.searchTerm ?? '');
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
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

    setPosts(posts: Post[]): void {
        if (this.content) {
            this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        }
        this.posts = posts.slice().reverse();
        console.log(posts);
    }

    scrollToBottomOfMessages() {
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollTop + 50;
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        this.refreshMetisConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts = true; // will be set to false in subscription
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate);
        }
    }

    private refreshMetisConversationPostContextFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course?.id,
            searchText: this.searchTerm ? this.searchTerm.trim() : undefined,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
            pagingEnabled: true,
            page: this.page - 1,
            pageSize: 50,
        };
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.currentPostContextFilter!.courseWideChannelIds = conversations.map((conversation) => conversation!.id!);
        });
    }

    postsTrackByFn = (index: number, post: Post): number => post.id!;

    setPostForThread(post: Post) {
        this.openThread.emit(post);
    }

    onSearch(searchInput: string) {
        this.searchTerm = searchInput;
        this.commandMetisToFetchPosts(true);
        console.log('search term: ' + this.searchTerm);
    }

    protected readonly getAsChannel = getAsChannelDTO;
}

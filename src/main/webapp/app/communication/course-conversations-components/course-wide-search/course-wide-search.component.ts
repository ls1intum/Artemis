import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnDestroy, OnInit, ViewEncapsulation, inject, input, output, viewChild, viewChildren } from '@angular/core';
import { faChevronLeft, faCircleNotch, faEnvelope, faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';
import { Course } from 'app/core/course/shared/entities/course.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { MetisService } from 'app/communication/service/metis.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/communication/metis.util';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { CourseSidebarService } from 'app/core/course/overview/services/course-sidebar.service';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { UserPublicInfoDTO } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-course-wide-search',
    templateUrl: './course-wide-search.component.html',
    styleUrls: ['./course-wide-search.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, TranslateDirective, FaIconComponent, FormsModule, ReactiveFormsModule, NgbTooltip, InfiniteScrollDirective, PostingThreadComponent, ArtemisTranslatePipe],
})
export class CourseWideSearchComponent implements OnInit, AfterViewInit, OnDestroy {
    courseWideSearchConfig = input.required<CourseWideSearchConfig>();

    readonly messages = viewChildren<ElementRef>('postingThread');
    readonly messages$ = toObservable(this.messages);
    readonly content = viewChild<ElementRef>('container');

    readonly openThread = output<Post>();

    course: Course;
    currentPostContextFilter?: PostContextFilter;
    // as set for the css class '.posting-infinite-scroll-container'
    messagesContainerHeight = 700;

    readonly faPlus = faPlus;
    readonly faFilter = faFilter;
    readonly faLongArrowAltUp = faLongArrowAltUp;
    readonly faLongArrowAltDown = faLongArrowAltDown;
    readonly faTimes = faTimes;
    readonly faEnvelope = faEnvelope;
    readonly faCircleNotch = faCircleNotch;
    readonly faChevronLeft = faChevronLeft;

    readonly SortDirection = SortDirection;
    readonly onNavigateToPost = output<Posting>();
    sortingOrder = SortDirection.ASCENDING;

    private ngUnsubscribe = new Subject<void>();
    public isFetchingPosts = true;
    totalNumberOfPosts = 0;
    posts: Post[] = [];
    private allConversationIds: number[] = [];
    previousScrollDistanceFromTop: number;
    page = 1;

    formGroup: FormGroup;

    getAsChannel = getAsChannelDTO;

    private courseSidebarService = inject(CourseSidebarService);
    private metisService = inject(MetisService);
    private metisConversationService = inject(MetisConversationService);
    private formBuilder = inject(FormBuilder);
    private cdr = inject(ChangeDetectorRef);

    ngOnInit() {
        this.subscribeToMetis();
        this.resetFormGroup();
        this.cdr.detectChanges();
        this.commandMetisToFetchPosts(true);
    }

    ngAfterViewInit() {
        this.messages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openSidebar() {
        this.courseSidebarService.openSidebar();
    }

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.setPosts(posts);
            this.isFetchingPosts = false;
        });
        this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.allConversationIds = conversations.map((conversation) => conversation.id!);
        });
    }

    setPosts(posts: Post[]): void {
        if (this.content()) {
            this.previousScrollDistanceFromTop = this.content()!.nativeElement.scrollHeight - this.content()!.nativeElement.scrollTop;
        }
        this.posts = posts.slice().reverse();
    }

    handleScrollOnNewMessage = () => {
        if (
            (this.posts.length > 0 && this.content() && this.content()!.nativeElement.scrollTop === 0 && this.page === 1) ||
            this.previousScrollDistanceFromTop === this.messagesContainerHeight
        ) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        if (!this.content()) return;
        this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollHeight;
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        if (!this.content()) return;
        this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollTop + 50;
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        this.refreshMetisConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts = true; // will be set to false in subscription
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate);
        }
    }

    private refreshMetisConversationPostContextFilter(): void {
        const searchConfig = this.courseWideSearchConfig();

        if (!searchConfig) return;

        let conversationIds = searchConfig.selectedConversations?.map((conversation) => conversation.id!);
        if (!conversationIds || conversationIds.length === 0) {
            conversationIds = this.allConversationIds;
        }

        this.currentPostContextFilter = {
            courseId: this.course?.id,
            searchText: searchConfig.searchTerm ? searchConfig.searchTerm.trim() : undefined,
            authorIds: searchConfig.selectedAuthors?.map((author) => author.id!),
            conversationIds: conversationIds,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            filterToCourseWide: searchConfig.filterToCourseWide,
            filterToUnresolved: searchConfig.filterToUnresolved,
            filterToAnsweredOrReacted: searchConfig.filterToAnsweredOrReacted,
            sortingOrder: searchConfig.sortingOrder,
            pagingEnabled: true,
            page: this.page - 1,
            pageSize: 50,
        };
    }

    postsTrackByFn = (index: number, post: Post): number => post.id!;

    setPostForThread(post: Post) {
        this.openThread.emit(post);
    }

    onSearch() {
        this.commandMetisToFetchPosts(true);
    }

    /**
     * Monitor the search config for changes to selected conversations
     */
    onSearchConfigSelectionChange(): void {
        const config = this.courseWideSearchConfig();
        if (!config) return;

        const hasSelectedConversations = config.selectedConversations?.length > 0;

        const filterToCourseWideControl = this.formGroup?.get('filterToCourseWide');
        if (filterToCourseWideControl) {
            if (hasSelectedConversations) {
                // When conversations are selected, disable the courseWide checkbox and set to false
                filterToCourseWideControl.setValue(false);
                filterToCourseWideControl.disable();
            } else {
                filterToCourseWideControl.enable();
            }

            config.filterToCourseWide = filterToCourseWideControl.value;
        }
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            filterToCourseWide: false,
            filterToUnresolved: false,
            filterToAnsweredOrReacted: false,
        });
    }

    onChangeSortDir(): void {
        this.sortingOrder = this.sortingOrder === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
        this.onSelectContext();
    }

    onSelectContext(): void {
        const searchConfig = this.courseWideSearchConfig();
        if (!searchConfig) return;
        searchConfig.filterToCourseWide = this.formGroup.get('filterToCourseWide')?.value;
        searchConfig.filterToUnresolved = this.formGroup.get('filterToUnresolved')?.value;
        searchConfig.filterToAnsweredOrReacted = this.formGroup.get('filterToAnsweredOrReacted')?.value;
        searchConfig.sortingOrder = this.sortingOrder;
        this.commandMetisToFetchPosts(true);
    }

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}

export class CourseWideSearchConfig {
    searchTerm: string;
    selectedConversations: ConversationDTO[];
    selectedAuthors: UserPublicInfoDTO[];
    filterToCourseWide: boolean;
    filterToUnresolved: boolean;
    filterToAnsweredOrReacted: boolean;
    sortingOrder: SortDirection;
}

import { AfterViewInit, Component, ElementRef, OnDestroy, OnInit, ViewEncapsulation, inject, input, output, signal, viewChild, viewChildren } from '@angular/core';
import { faChevronLeft, faCircleNotch, faEnvelope, faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { toObservable } from '@angular/core/rxjs-interop';
import { Course } from 'app/course/shared/entities/course.model';
import { getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { CommunicationService } from 'app/communication/service/communication.service';
import { CourseConversationsService } from 'app/communication/service/course-conversations.service';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/communication/communication.util';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { CourseSidebarService } from 'app/course/overview/services/course-sidebar.service';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { UserPublicInfoDTO } from 'app/account/user/user.model';

@Component({
    selector: 'jhi-course-wide-search',
    templateUrl: './course-wide-search.component.html',
    styleUrls: ['./course-wide-search.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, TranslateDirective, FaIconComponent, FormsModule, ReactiveFormsModule, NgbTooltip, InfiniteScrollDirective, PostingThreadComponent, ArtemisTranslatePipe],
})
export class CourseWideSearchComponent implements OnInit, AfterViewInit, OnDestroy {
    private courseSidebarService = inject(CourseSidebarService);
    private communicationService = inject(CommunicationService);
    private courseConversationsService = inject(CourseConversationsService);
    private formBuilder = inject(FormBuilder);

    courseWideSearchConfig = input.required<CourseWideSearchConfig>();

    readonly messages = viewChildren<ElementRef>('postingThread');
    readonly messages$ = toObservable(this.messages);
    readonly content = viewChild<ElementRef>('container');

    readonly openThread = output<Post>();

    course?: Course;
    currentPostContextFilter?: PostContextFilter;
    readonly isAtLeastTutor = signal(false);

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
    readonly sortingOrder = signal(SortDirection.ASCENDING);

    private ngUnsubscribe = new Subject<void>();
    readonly isFetchingPosts = signal(true);
    readonly totalNumberOfPosts = signal(0);
    readonly posts = signal<Post[]>([]);
    private allConversationIds: number[] = [];
    private pendingPageScrollDistance: number | undefined;
    private preserveScrollOnNextPage = false;
    private postRequestId = 0;
    private initialScrollPositioned = false;
    private scrollFrame: number | undefined;
    page = 1;

    formGroup!: FormGroup; // set in resetFormGroup() from ngOnInit()

    getAsChannel = getAsChannelDTO;

    ngOnInit() {
        this.subscribeToPosts();
        this.isAtLeastTutor.set(this.communicationService.currentUserIsAtLeastTutorInCourse());
        this.resetFormGroup();
        this.fetchPosts(true);
    }

    ngAfterViewInit() {
        this.messages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
    }

    ngOnDestroy() {
        this.postRequestId++;
        if (this.scrollFrame !== undefined) window.cancelAnimationFrame(this.scrollFrame);
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openSidebar() {
        this.courseSidebarService.openSidebar();
    }

    private subscribeToPosts() {
        this.communicationService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            if (this.currentPostContextFilter?.filterToUnresolved) {
                posts = posts.filter((post) => post.resolved === false);
            }
            this.setPosts(posts);
        });
        this.communicationService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts.set(totalNumberOfPosts);
        });
        this.courseConversationsService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.allConversationIds = conversations.map((conversation) => conversation.id!);
        });
    }

    setPosts(posts: Post[]): void {
        this.posts.set(posts.slice().reverse());
    }

    handleScrollOnNewMessage = () => {
        if (this.isFetchingPosts() || this.posts().length === 0 || !this.content()) return;
        if (this.scrollFrame !== undefined) window.cancelAnimationFrame(this.scrollFrame);
        this.scrollFrame = requestAnimationFrame(() => {
            this.scrollFrame = undefined;
            const element = this.content()?.nativeElement;
            if (!element) return;
            if (this.pendingPageScrollDistance !== undefined) {
                element.scrollTop = element.scrollHeight - this.pendingPageScrollDistance;
                this.pendingPageScrollDistance = undefined;
            } else if (!this.initialScrollPositioned) {
                this.scrollToBottomOfMessages();
            }
            this.initialScrollPositioned = true;
        });
    };

    scrollToBottomOfMessages() {
        if (!this.content()) return;
        this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollHeight;
    }

    fetchNextPage() {
        if (this.isFetchingPosts() || this.posts().length >= this.totalNumberOfPosts()) return;
        this.preserveScrollOnNextPage = true;
        const previousPage = this.page;
        this.page += 1;
        this.fetchPosts(false, previousPage);
    }

    public fetchPosts(forceUpdate = false, previousPage = this.page) {
        this.refreshConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts.set(true);
            const requestId = ++this.postRequestId;
            this.communicationService.getFilteredPosts(this.currentPostContextFilter, forceUpdate, undefined, {
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
                    if (applied) {
                        this.handleScrollOnNewMessage();
                    }
                },
            });
        }
    }

    private refreshConversationPostContextFilter(): void {
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
            filterToUnverifiedIris: this.isAtLeastTutor() && searchConfig.filterToUnverifiedIris,
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
        if (this.scrollFrame !== undefined) window.cancelAnimationFrame(this.scrollFrame);
        this.scrollFrame = undefined;
        this.page = 1;
        this.initialScrollPositioned = false;
        this.pendingPageScrollDistance = undefined;
        this.preserveScrollOnNextPage = false;
        this.fetchPosts(true);
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
            filterToUnverifiedIris: false,
        });
    }

    onChangeSortDir(): void {
        this.sortingOrder.update((order) => (order === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING));
        this.onSelectContext();
    }

    onSelectContext(): void {
        const searchConfig = this.courseWideSearchConfig();
        if (!searchConfig) return;
        searchConfig.filterToCourseWide = this.formGroup.get('filterToCourseWide')?.value;
        searchConfig.filterToUnresolved = this.formGroup.get('filterToUnresolved')?.value;
        searchConfig.filterToAnsweredOrReacted = this.formGroup.get('filterToAnsweredOrReacted')?.value;
        searchConfig.filterToUnverifiedIris = this.isAtLeastTutor() && this.formGroup.get('filterToUnverifiedIris')?.value;
        searchConfig.sortingOrder = this.sortingOrder();
        this.onSearch();
    }

    protected onTriggerNavigateToPost(post: Posting) {
        this.onNavigateToPost.emit(post);
    }
}

export class CourseWideSearchConfig {
    searchTerm = '';
    selectedConversations: ConversationDTO[] = [];
    selectedAuthors: UserPublicInfoDTO[] = [];
    filterToCourseWide = false;
    filterToUnresolved = false;
    filterToAnsweredOrReacted = false;
    filterToUnverifiedIris = false;
    sortingOrder = SortDirection.ASCENDING;
}

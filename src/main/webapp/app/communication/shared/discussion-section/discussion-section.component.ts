import { AfterViewInit, Component, ElementRef, OnDestroy, afterRenderEffect, effect, inject, input, signal, untracked, viewChild, viewChildren } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { DisplayPriority, PageType, PostSortCriterion, SortDirection } from 'app/communication/communication.util';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Subject, combineLatest, map, takeUntil } from 'rxjs';
import { CommunicationService } from 'app/communication/service/communication.service';
import { Post } from 'app/communication/shared/entities/post.model';
import { User } from 'app/account/user/user.model';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { HttpResponse } from '@angular/common/http';
import { faArrowLeft, faChevronLeft, faChevronRight, faLongArrowRight } from '@fortawesome/free-solid-svg-icons';
import { CourseDiscussionDirective } from 'app/communication/directive/course-discussion.directive';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Channel, ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { InfiniteScrollDirective } from 'app/shared-ui/infinite-scroll/infinite-scroll.directive';
import { PostingThreadComponent } from 'app/communication/posting-thread/posting-thread.component';
import { MessageInlineInputComponent } from 'app/communication/message/message-inline-input/message-inline-input.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { toObservable } from '@angular/core/rxjs-interop';
import { ChannelService } from 'app/communication/conversations/service/channel.service';
import { CourseStorageService } from 'app/course/manage/services/course-storage.service';
import { AccountService } from 'app/core/auth/account.service';
import { SearchFilterComponent } from 'app/shared-ui/search-filter/search-filter.component';
import { ResizableDirective } from 'app/shared-ui/directives/resizable.directive';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-discussion-section',
    templateUrl: './discussion-section.component.html',
    styleUrls: ['./discussion-section.component.scss'],
    imports: [
        FontAwesomeModule,
        InfiniteScrollDirective,
        FormsModule,
        ReactiveFormsModule,
        PostingThreadComponent,
        MessageInlineInputComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        NgbTooltipModule,
        SearchFilterComponent,
        ResizableDirective,
    ],
    providers: [CommunicationService],
})
export class DiscussionSectionComponent extends CourseDiscussionDirective implements AfterViewInit, OnDestroy {
    private channelService = inject(ChannelService);
    private courseStorageService = inject(CourseStorageService);
    private accountService = inject(AccountService);
    private activatedRoute = inject(ActivatedRoute);
    private router = inject(Router);
    private formBuilder = inject(FormBuilder);

    exercise = input<Exercise>();
    lecture = input<Lecture>();
    embedded = input<boolean>(false);

    readonly postCreateEditModal = viewChild<PostCreateEditModalComponent>(PostCreateEditModalComponent);
    readonly messages = viewChildren<ElementRef>('postingThread');
    readonly messages$ = toObservable(this.messages);
    readonly content = viewChild<ElementRef>('itemsContainer');

    private ngUnsubscribe = new Subject<void>();
    private previousScrollDistanceFromTop = 0;
    private page = 1;
    private postRequestId = 0;
    private isFetchingPosts = false;
    private readonly pendingPageScrollDistance = signal<number | undefined>(undefined);
    private pageScrollFrame?: number;
    private readonly PAGE_SIZE = 50;
    private totalNumberOfPosts = 0;
    private get messagesContainerHeight(): number {
        return this.content()?.nativeElement.clientHeight ?? 700;
    }
    private viewChildrenInitialized = false;

    readonly channel = signal<ChannelDTO | undefined>(undefined);
    readonly noChannelAvailable = signal(false);
    readonly collapsed = signal(false);
    currentPostId?: number;
    readonly currentPost = signal<Post | undefined>(undefined);
    currentUser?: User;
    readonly shouldSendMessage = signal(false);
    readonly PAGE_TYPE = PageType.PAGE_SECTION;

    // Icons
    faChevronRight = faChevronRight;
    faChevronLeft = faChevronLeft;
    faArrowLeft = faArrowLeft;
    faLongArrowRight = faLongArrowRight;

    constructor() {
        super();
        this.currentSortDirection.set(SortDirection.DESCENDING);
        effect(() => {
            const exerciseValue = this.exercise();
            const lectureValue = this.lecture();
            untracked(() => this.loadData(exerciseValue, lectureValue));
        });
        afterRenderEffect(() => {
            this.messages();
            const distance = this.pendingPageScrollDistance();
            if (distance === undefined) return;
            untracked(() => {
                if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
                const requestId = this.postRequestId;
                this.pageScrollFrame = window.requestAnimationFrame(() => {
                    this.pageScrollFrame = undefined;
                    if (requestId !== this.postRequestId) return;
                    const element = this.content()?.nativeElement;
                    if (element) element.scrollTop = element.scrollHeight - distance;
                    this.pendingPageScrollDistance.set(undefined);
                });
            });
        });
    }

    loadData(exercise?: Exercise, lecture?: Lecture): void {
        this.paramSubscription = combineLatest({
            params: this.activatedRoute.params,
            queryParams: this.activatedRoute.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            this.currentPostId = +routeParams.queryParams.postId;
            const courseId = exercise?.course?.id ?? lecture?.course?.id;
            if (courseId) {
                this.course.set(this.courseStorageService.getCourse(courseId));
            }
            this.communicationService.setCourse(this.course());
            this.communicationService.setPageType(this.PAGE_TYPE);
            if (routeParams.params.courseId) {
                this.setChannel(routeParams.params.courseId);
            } else if (this.course()?.id) {
                this.setChannel(this.course()!.id!);
            }
            this.createEmptyPost();
            this.resetFormGroup();
        });
        this.postsSubscription = this.communicationService.posts.subscribe((posts: Post[]) => {
            if (this.viewChildrenInitialized && this.content()) {
                this.previousScrollDistanceFromTop = this.content()!.nativeElement.scrollHeight - this.content()!.nativeElement.scrollTop;
            }
            this.posts.set(
                posts
                    .slice()
                    .sort((a, b) => {
                        if (a.displayPriority === DisplayPriority.PINNED && b.displayPriority !== DisplayPriority.PINNED) {
                            return 1;
                        }
                        if (a.displayPriority !== DisplayPriority.PINNED && b.displayPriority === DisplayPriority.PINNED) {
                            return -1;
                        }
                        return 0;
                    })
                    .reverse(),
            );
            if (this.currentPostId && this.posts().length > 0) {
                this.currentPost.set(this.posts().find((post) => post.id === this.currentPostId));
            }
        });
        void this.accountService.identity().then((user: User | undefined) => {
            this.currentUser = user!;
        });
        this.communicationService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        ++this.postRequestId;
        this.clearPageScroll();
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        super.onDestroy();
        this.postCreateEditModal()?.close();
    }

    /**
     * on changing the sort direction via icon, the communication service is invoked to deliver the posts for the currently set context,
     * sorted on the server
     */
    onChangeSortDir(): void {
        this.currentSortDirection.set(this.currentSortDirection() === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING);
        this.onSelectContext();
    }

    /**
     * Set the channel for the discussion section, either for a lecture or an exercise
     * @param courseId
     */
    setChannel(courseId: number): void {
        const getChannel = () => {
            return {
                next: (channel: ChannelDTO | null) => {
                    this.channel.set(channel ?? undefined);
                    this.resetFormGroup();
                    this.setFilterAndSort();

                    if (!this.channel()?.id) {
                        this.noChannelAvailable.set(true);
                        this.collapsed.set(true);
                        return;
                    }

                    this.fetchPosts(true);

                    this.createEmptyPost();
                    this.resetFormGroup();
                },
            };
        };

        // Currently, an additional REST call is made to retrieve the channel associated with the lecture/exercise
        // TODO: Add the channel to the response for loading the lecture/exercise
        if (this.lecture()) {
            this.channelService
                .getChannelOfLecture(courseId, this.lecture()!.id!)
                .pipe(map((res: HttpResponse<ChannelDTO>) => res.body))
                .subscribe(getChannel());
        } else if (this.exercise()) {
            this.channelService
                .getChannelOfExercise(courseId, this.exercise()!.id!)
                .pipe(map((res: HttpResponse<ChannelDTO>) => res.body))
                .subscribe(getChannel());
        }
    }

    /**
     * invoke communication service to create an empty default post that is needed on initialization of a modal to create a post,
     * this empty post has either exercise or lecture set as context, depending on if this component holds an exercise or a lecture reference
     */
    createEmptyPost(): void {
        if (this.channel()) {
            const conversation = this.channel() as Channel;
            this.shouldSendMessage.set(false);
            this.createdPost.set(this.communicationService.createEmptyPostForContext(conversation));
        } else {
            this.createdPost.set(this.communicationService.createEmptyPostForContext());
        }
    }

    ngAfterViewInit(): void {
        this.messages$.pipe(takeUntil(this.ngUnsubscribe)).subscribe(() => {
            this.handleScrollOnNewMessage();
        });

        this.viewChildrenInitialized = true;
    }

    handleScrollOnNewMessage = () => {
        if (this.pendingPageScrollDistance() !== undefined) return;
        if ((this.posts().length > 0 && this.content()?.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        if (this.viewChildrenInitialized && this.content()?.nativeElement) {
            this.content()!.nativeElement.scrollTop = this.content()!.nativeElement.scrollHeight;
        }
    }

    /** Loads one older page at a time. An initial request without an applied response retries page zero. */
    fetchNextPage(): void {
        if (this.isFetchingPosts || this.pendingPageScrollDistance() !== undefined) return;
        if (this.page === 0) {
            this.page = 1;
            this.fetchPosts(true, 0);
            return;
        }
        if (this.posts().length < this.totalNumberOfPosts) {
            const previousPage = this.page;
            this.page += 1;
            this.fetchPosts(false, previousPage);
        }
    }

    /**
     * Fetches this page with request-scoped completion. Live publications cannot unlock pagination, and superseded
     * completions cannot change a newer context. Restore the last applied page when no response was applied.
     * @param forceUpdate whether to replace the context rather than append a page
     * @param previousPage the last applied one-based page; zero means the initial context has not loaded
     */
    public fetchPosts(forceUpdate = false, previousPage = forceUpdate ? 0 : this.page): void {
        if (!this.currentPostContextFilter || (this.isFetchingPosts && !forceUpdate)) return;
        if (forceUpdate) this.clearPageScroll();
        const requestId = ++this.postRequestId;
        this.isFetchingPosts = true;
        if (forceUpdate) this.isLoading.set(true);
        this.currentPostContextFilter = cloneWith(this.currentPostContextFilter, { page: this.page - 1 });
        this.communicationService.getFilteredPosts(this.currentPostContextFilter, forceUpdate, this.channel(), {
            beforeApply: () => {
                if (requestId !== this.postRequestId || forceUpdate) return;
                // Capture after any reader movement/live updates, immediately before this page changes the list.
                const element = this.content()?.nativeElement;
                this.pendingPageScrollDistance.set(element ? element.scrollHeight - element.scrollTop : undefined);
            },
            complete: (applied) => {
                if (requestId !== this.postRequestId) return;
                this.isFetchingPosts = false;
                this.isLoading.set(false);
                if (!applied) {
                    this.clearPageScroll();
                    this.page = previousPage;
                    this.currentPostContextFilter = cloneWith(this.currentPostContextFilter, { page: Math.max(previousPage - 1, 0) });
                }
            },
        });
    }

    /** Cancels deferred viewport restoration when a context is replaced, fails or is destroyed. */
    private clearPageScroll(): void {
        if (this.pageScrollFrame !== undefined) window.cancelAnimationFrame(this.pageScrollFrame);
        this.pageScrollFrame = undefined;
        this.pendingPageScrollDistance.set(undefined);
    }

    /** Replaces the context through the same request lifecycle used by initial loads and pagination. */
    override onSelectContext(): void {
        this.setFilterAndSort();
        this.fetchPosts(true);
    }

    /**
     * sets the filter options after receiving user input
     */
    setFilterAndSort(): void {
        ++this.postRequestId;
        this.clearPageScroll();
        this.isFetchingPosts = false;
        this.page = 1;
        this.scrollToBottomOfMessages();
        this.currentPostContextFilter = {
            courseId: undefined,
            conversationIds: this.channel()?.id ? [this.channel()!.id!] : undefined,
            authorIds: this.formGroup.get('filterToOwn')?.value && this.currentUser?.id ? [this.currentUser.id] : undefined,
            searchText: this.searchText?.trim(),
            filterToUnresolved: this.formGroup.get('filterToUnresolved')?.value,
            filterToAnsweredOrReacted: this.formGroup.get('filterToAnsweredOrReacted')?.value,
            pagingEnabled: true,
            page: 0,
            pageSize: this.PAGE_SIZE,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: this.currentSortDirection(),
        };
    }

    resetCurrentPost() {
        this.currentPost.set(undefined);
        this.currentPostId = undefined;
        void this.router.navigate([], {
            queryParams: {
                postId: this.currentPostId,
            },
            queryParamsHandling: 'merge',
        });
    }

    /**
     * by default, the form group fields are set to show all posts of the current exercise or lecture
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            conversationId: this.channel()?.id,
            exerciseId: this.exercise()?.id,
            lectureId: this.lecture()?.id,
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    toggleSendMessage(): void {
        this.shouldSendMessage.update((value) => !value);
    }

    /**
     * on receiving a new search term, updates the search text and triggers a post reload with the current filter context
     */
    onSearch(searchText: string): void {
        this.searchText = searchText;
        this.onSelectContext();
    }
}

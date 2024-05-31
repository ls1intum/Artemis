import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Subject, take, takeUntil } from 'rxjs';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course } from 'app/entities/course.model';
import { PageType, SortDirection } from 'app/shared/metis/metis.util';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    styleUrls: ['./course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [MetisService],
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    course?: Course;
    isLoading = false;
    isServiceSetUp = false;
    postInThread?: Post;
    activeConversation?: ConversationDTO = undefined;
    conversationsOfUser: ConversationDTO[] = [];
    channelSearchCollapsed = false;

    // set undefined so nothing gets displayed until isCodeOfConductAccepted is loaded
    isCodeOfConductAccepted?: boolean;
    isCodeOfConductPresented: boolean = false;

    @ViewChild(CourseWideSearchComponent)
    courseWideSearch: CourseWideSearchComponent;

    courseWideSearchConfig: CourseWideSearchConfig;
    courseWideSearchTerm = '';
    formGroup: FormGroup;
    readonly documentationType: DocumentationType = 'Communications';
    readonly ButtonType = ButtonType;
    readonly SortDirection = SortDirection;
    sortingOrder = SortDirection.ASCENDING;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;
    faLongArrowAltUp = faLongArrowAltUp;
    faLongArrowAltDown = faLongArrowAltDown;

    // MetisConversationService is created in course overview, so we can use it here
    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private metisConversationService: MetisConversationService,
        private metisService: MetisService,
        private formBuilder: FormBuilder,
    ) {}

    getAsChannel = getAsChannelDTO;

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            if (this.postInThread?.id && posts) {
                this.postInThread = posts.find((post) => post.id === this.postInThread?.id);
            }
        });
    }

    private setupMetis() {
        this.metisService.setPageType(PageType.OVERVIEW);
        this.metisService.setCourse(this.course!);
    }

    ngOnInit(): void {
        this.isLoading = true;
        this.metisConversationService.isServiceSetup$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isServiceSetUp: boolean) => {
            if (isServiceSetUp) {
                this.course = this.metisConversationService.course;
                this.initializeCourseWideSearchConfig();
                this.resetFormGroup();
                this.setupMetis();
                this.subscribeToMetis();
                this.subscribeToQueryParameter();
                // service is fully set up, now we can subscribe to the respective observables
                this.subscribeToActiveConversation();
                this.subscribeToIsCodeOfConductAccepted();
                this.subscribeToIsCodeOfConductPresented();
                this.subscribeToConversationsOfUser();
                this.subscribeToLoading();
                this.updateQueryParameters();
                this.metisConversationService.checkIsCodeOfConductAccepted(this.course!);
                this.isServiceSetUp = true;
            }
        });
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams.pipe(take(1), takeUntil(this.ngUnsubscribe)).subscribe((queryParams) => {
            if (queryParams.conversationId) {
                this.metisConversationService.setActiveConversation(Number(queryParams.conversationId));
            }
            if (queryParams.messageId) {
                this.postInThread = { id: Number(queryParams.messageId) } as Post;
            } else {
                this.postInThread = undefined;
            }
        });
    }

    updateQueryParameters() {
        this.router.navigate([], {
            relativeTo: this.activatedRoute,
            queryParams: {
                conversationId: this.activeConversation?.id,
            },
            replaceUrl: true,
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            this.activeConversation = conversation;
            this.updateQueryParameters();
        });
    }

    private subscribeToIsCodeOfConductAccepted() {
        this.metisConversationService.isCodeOfConductAccepted$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isCodeOfConductAccepted: boolean) => {
            this.isCodeOfConductAccepted = isCodeOfConductAccepted;
        });
    }

    private subscribeToIsCodeOfConductPresented() {
        this.metisConversationService.isCodeOfConductPresented$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isCodeOfConductPresented: boolean) => {
            this.isCodeOfConductPresented = isCodeOfConductPresented;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.conversationsOfUser = conversations ?? [];
        });
    }

    private subscribeToLoading() {
        this.metisConversationService.isLoading$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((isLoading: boolean) => {
            this.isLoading = isLoading;
        });
    }

    acceptCodeOfConduct() {
        if (this.course) {
            this.metisConversationService.acceptCodeOfConduct(this.course);
        }
    }

    initializeCourseWideSearchConfig() {
        this.courseWideSearchConfig = new CourseWideSearchConfig();
        this.courseWideSearchConfig.searchTerm = '';
        this.courseWideSearchConfig.filterToUnresolved = false;
        this.courseWideSearchConfig.filterToOwn = false;
        this.courseWideSearchConfig.filterToAnsweredOrReacted = false;
        this.courseWideSearchConfig.sortingOrder = SortDirection.ASCENDING;
    }

    onSearch() {
        this.activeConversation = undefined;
        this.courseWideSearchConfig.searchTerm = this.courseWideSearchTerm;
        this.courseWideSearch?.onSearch();
    }

    onSelectContext(): void {
        this.courseWideSearchConfig.filterToUnresolved = this.formGroup.get('filterToUnresolved')?.value;
        this.courseWideSearchConfig.filterToOwn = this.formGroup.get('filterToOwn')?.value;
        this.courseWideSearchConfig.filterToAnsweredOrReacted = this.formGroup.get('filterToAnsweredOrReacted')?.value;
        this.courseWideSearchConfig.sortingOrder = this.sortingOrder;
        if (!this.activeConversation) {
            this.onSearch();
        }
    }

    onChangeSortDir(): void {
        this.sortingOrder = this.sortingOrder === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
        this.onSelectContext();
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    collapseChannelSearch() {
        this.channelSearchCollapsed = !this.channelSearchCollapsed;
    }
}

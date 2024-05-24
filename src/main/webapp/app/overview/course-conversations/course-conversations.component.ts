import { Component, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { ActivatedRoute, Router } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, from, take, takeUntil } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course } from 'app/entities/course.model';
import { PageType, SortDirection } from 'app/shared/metis/metis.util';
import { faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { DocumentationType } from 'app/shared/components/documentation-button/documentation-button.component';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { AccordionGroups, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';

const DEFAULT_CHANNEL_GROUPS: AccordionGroups = {
    favoriteChannels: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    groupChats: { entityData: [] },
    directMessages: { entityData: [] },
    hiddenChannels: { entityData: [] },
};

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

    conversationSelected: boolean = true;
    sidebarData: SidebarData;
    accordionConversationGroups: AccordionGroups = DEFAULT_CHANNEL_GROUPS;
    sidebarConversations: SidebarCardElement[] = [];
    isCollapsed: boolean = false;

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
        private courseOverviewService: CourseOverviewService,
        private modalService: NgbModal,
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
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('conversation');
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
                this.prepareSidebarData();
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

    prepareSidebarData() {
        this.sidebarConversations = this.courseOverviewService.mapConversationsToSidebarCardElements(this.conversationsOfUser);
        this.accordionConversationGroups = this.courseOverviewService.groupConversationsByChannelType(this.conversationsOfUser);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'conversation',
            storageId: 'conversation',
            groupedData: this.accordionConversationGroups,
            ungroupedData: this.sidebarConversations,
        };
    }

    onConversationSelected(conversationId: number) {
        this.metisConversationService.setActiveConversation(conversationId);
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('conversation', this.isCollapsed);
    }

    onAccordionPlusButtonPressed(chatType: string) {
        if (chatType === 'groupChats') {
            this.openCreateGroupChatDialog();
        } else if (chatType === 'directMessages') {
            this.openCreateOneToOneChatDialog();
        } else {
            this.openChannelOverviewDialog(chatType);
        }
    }

    openCreateGroupChatDialog() {
        const modalRef: NgbModalRef = this.modalService.open(GroupChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((chatPartners: UserPublicInfoDTO[]) => {
                this.metisConversationService.createGroupChat(chatPartners?.map((partner) => partner.login!)).subscribe({
                    complete: () => {
                        this.metisConversationService.forceRefresh().subscribe({
                            complete: () => {},
                        });
                        this.prepareSidebarData();
                    },
                });
            });
    }

    openCreateOneToOneChatDialog() {
        const modalRef: NgbModalRef = this.modalService.open(OneToOneChatCreateDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((chatPartner: UserPublicInfoDTO) => {
                if (chatPartner?.login) {
                    this.metisConversationService.createOneToOneChat(chatPartner.login).subscribe({
                        complete: () => {
                            this.metisConversationService.forceRefresh().subscribe({
                                complete: () => {},
                            });
                            this.prepareSidebarData();
                        },
                    });
                }
            });
    }

    openChannelOverviewDialog(groupKey: string) {
        const subType = this.getChannelSubType(groupKey);
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, defaultFirstLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = subType === ChannelSubType.GENERAL ? this.metisConversationService.createChannel : undefined;
        modalRef.componentInstance.channelSubType = subType;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(catchError(() => EMPTY))
            .subscribe((result) => {
                const [newActiveConversation, isModificationPerformed] = result;
                if (isModificationPerformed) {
                    this.metisConversationService.forceRefresh(!newActiveConversation, true).subscribe({
                        complete: () => {
                            if (newActiveConversation) {
                                this.metisConversationService.setActiveConversation(newActiveConversation);
                            }
                        },
                    });
                } else {
                    if (newActiveConversation) {
                        this.metisConversationService.setActiveConversation(newActiveConversation);
                    }
                }
                this.prepareSidebarData();
            });
    }

    getChannelSubType(groupKey: string) {
        if (groupKey === 'exerciseChannels') {
            return ChannelSubType.EXERCISE;
        }
        if (groupKey === 'generalChannels') {
            return ChannelSubType.GENERAL;
        }
        if (groupKey === 'lectureChannels') {
            return ChannelSubType.LECTURE;
        }
        if (groupKey === 'examChannels') {
            return ChannelSubType.EXAM;
        }
        return ChannelSubType.GENERAL;
    }
}

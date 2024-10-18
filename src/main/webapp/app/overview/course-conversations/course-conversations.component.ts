import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Subject, Subscription, from, take, takeUntil } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { PageType, SortDirection } from 'app/shared/metis/metis.util';
import { faBan, faComment, faComments, faFile, faFilter, faGraduationCap, faHeart, faList, faMessage, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { AccordionGroups, ChannelAccordionShowAdd, ChannelAlwaysShow, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarData } from 'app/types/sidebar';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { defaultFirstLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';

const DEFAULT_CHANNEL_GROUPS: AccordionGroups = {
    favoriteChannels: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    hiddenChannels: { entityData: [] },
};

const CHANNEL_TYPE_SHOW_ADD_OPTION: ChannelAccordionShowAdd = {
    generalChannels: true,
    exerciseChannels: true,
    examChannels: true,
    groupChats: true,
    directMessages: true,
    favoriteChannels: false,
    lectureChannels: true,
    hiddenChannels: false,
};

const CHANNEL_TYPE_ICON: ChannelTypeIcons = {
    generalChannels: faMessage,
    exerciseChannels: faList,
    examChannels: faGraduationCap,
    groupChats: faComments,
    directMessages: faComment,
    favoriteChannels: faHeart,
    lectureChannels: faFile,
    hiddenChannels: faBan,
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    generalChannels: true,
    exerciseChannels: true,
    examChannels: true,
    groupChats: true,
    directMessages: true,
    favoriteChannels: false,
    lectureChannels: true,
    hiddenChannels: true,
};

const ALWAYS_SHOW_CHANNEL: ChannelAlwaysShow = {
    generalChannels: true,
    exerciseChannels: false,
    examChannels: false,
    groupChats: true,
    directMessages: true,
    favoriteChannels: true,
    lectureChannels: false,
    hiddenChannels: false,
};

@Component({
    selector: 'jhi-course-conversations',
    templateUrl: './course-conversations.component.html',
    styleUrls: ['../course-overview.scss', './course-conversations.component.scss'],
    encapsulation: ViewEncapsulation.None,
    providers: [MetisService],
})
export class CourseConversationsComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();
    course?: Course;
    isLoading = false;
    isServiceSetUp = false;
    messagingEnabled = false;
    postInThread?: Post;
    activeConversation?: ConversationDTO = undefined;
    conversationsOfUser: ConversationDTO[] = [];
    channelSearchCollapsed = true;

    conversationSelected = true;
    sidebarData: SidebarData;
    accordionConversationGroups: AccordionGroups;
    sidebarConversations: SidebarCardElement[] = [];
    profileSubscription?: Subscription;
    isCollapsed = false;
    isProduction = true;
    isTestServer = false;

    readonly CHANNEL_TYPE_SHOW_ADD_OPTION = CHANNEL_TYPE_SHOW_ADD_OPTION;
    readonly CHANNEL_TYPE_ICON = CHANNEL_TYPE_ICON;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    readonly CHANNEL_VISIBILITY_EMPTY = ALWAYS_SHOW_CHANNEL;

    // set undefined so nothing gets displayed until isCodeOfConductAccepted is loaded
    isCodeOfConductAccepted?: boolean;
    isCodeOfConductPresented = false;

    @ViewChild(CourseWideSearchComponent)
    courseWideSearch: CourseWideSearchComponent;
    @ViewChild('courseWideSearchInput')
    searchElement: ElementRef;

    courseWideSearchConfig: CourseWideSearchConfig;
    courseWideSearchTerm = '';
    readonly ButtonType = ButtonType;

    // Icons
    faPlus = faPlus;
    faTimes = faTimes;
    faFilter = faFilter;
    faSearch = faSearch;

    // MetisConversationService is created in course overview, so we can use it here
    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private metisConversationService: MetisConversationService,
        private metisService: MetisService,
        private courseOverviewService: CourseOverviewService,
        private modalService: NgbModal,
        private profileService: ProfileService,
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
                this.initializeSidebarAccordions();
                this.setupMetis();
                this.subscribeToMetis();
                this.subscribeToQueryParameter();
                // service is fully set up, now we can subscribe to the respective observables
                this.subscribeToActiveConversation();
                this.subscribeToIsCodeOfConductAccepted();
                this.subscribeToIsCodeOfConductPresented();
                this.subscribeToConversationsOfUser();
                this.updateQueryParameters();
                this.prepareSidebarData();
                this.metisConversationService.checkIsCodeOfConductAccepted(this.course!);
                this.isServiceSetUp = true;
                this.isLoading = false;
            }
        });

        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
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
        this.profileSubscription?.unsubscribe();
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

    initializeSidebarAccordions() {
        this.messagingEnabled = isMessagingEnabled(this.course);
        this.accordionConversationGroups = this.messagingEnabled
            ? { ...DEFAULT_CHANNEL_GROUPS, groupChats: { entityData: [] }, directMessages: { entityData: [] } }
            : DEFAULT_CHANNEL_GROUPS;
    }

    onSearch() {
        this.metisConversationService.setActiveConversation(undefined);
        this.updateQueryParameters();
        this.courseWideSearchConfig.searchTerm = this.courseWideSearchTerm;
        this.courseWideSearch?.onSearch();
    }

    prepareSidebarData() {
        this.metisConversationService.forceRefresh().subscribe({
            complete: () => {
                this.sidebarConversations = this.courseOverviewService.mapConversationsToSidebarCardElements(this.conversationsOfUser);
                this.accordionConversationGroups = this.courseOverviewService.groupConversationsByChannelType(this.conversationsOfUser, this.messagingEnabled);
                this.updateSidebarData();
            },
        });
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'conversation',
            storageId: 'conversation',
            groupedData: this.accordionConversationGroups,
            ungroupedData: this.sidebarConversations,
            showAccordionAddOption: true,
            showAccordionLeadingIcon: true,
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
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((chatPartners: UserPublicInfoDTO[]) => {
                this.metisConversationService.createGroupChat(chatPartners?.map((partner) => partner.login!)).subscribe({
                    complete: () => {
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
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((chatPartner: UserPublicInfoDTO) => {
                if (chatPartner?.login) {
                    this.metisConversationService.createOneToOneChat(chatPartner.login).subscribe({
                        complete: () => {
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
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
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

    toggleChannelSearch() {
        this.channelSearchCollapsed = !this.channelSearchCollapsed;
    }

    @HostListener('document:keydown', ['$event'])
    handleSearchShortcut(event: KeyboardEvent) {
        if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
            event.preventDefault();
            this.searchElement.nativeElement.focus();
        }
    }
}

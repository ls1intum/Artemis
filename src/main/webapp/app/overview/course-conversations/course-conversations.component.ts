import { Component, ElementRef, EventEmitter, HostListener, OnDestroy, OnInit, ViewChild, ViewEncapsulation, inject } from '@angular/core';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';
import { ActivatedRoute, Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { EMPTY, Observable, Subject, Subscription, from, take, takeUntil } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged } from 'rxjs/operators';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Course, isMessagingEnabled } from 'app/entities/course.model';
import { PageType, SortDirection } from 'app/shared/metis/metis.util';
import { faBan, faComment, faComments, faFile, faFilter, faGraduationCap, faHeart, faList, faMessage, faPlus, faSearch, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ButtonType } from 'app/shared/components/button.component';
import { CourseWideSearchComponent, CourseWideSearchConfig } from 'app/overview/course-conversations/course-wide-search/course-wide-search.component';
import { AccordionGroups, ChannelAccordionShowAdd, ChannelTypeIcons, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/types/sidebar';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { GroupChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/group-chat-create-dialog/group-chat-create-dialog.component';
import { defaultFirstLayerDialogOptions, defaultSecondLayerDialogOptions } from 'app/overview/course-conversations/other/conversation.util';
import { UserPublicInfoDTO } from 'app/core/user/user.model';
import { OneToOneChatCreateDialogComponent } from 'app/overview/course-conversations/dialogs/one-to-one-chat-create-dialog/one-to-one-chat-create-dialog.component';
import { ChannelAction, ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';
import { LayoutService } from 'app/shared/breakpoints/layout.service';
import { CustomBreakpointNames } from 'app/shared/breakpoints/breakpoints.service';

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

const DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
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
    private closeSidebarEventSubscription: Subscription;
    private openSidebarEventSubscription: Subscription;
    private toggleSidebarEventSubscription: Subscription;
    private breakpointSubscription: Subscription;
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
    isMobile = false;

    readonly CHANNEL_TYPE_SHOW_ADD_OPTION = CHANNEL_TYPE_SHOW_ADD_OPTION;
    readonly CHANNEL_TYPE_ICON = CHANNEL_TYPE_ICON;
    readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

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

    createChannelFn?: (channel: ChannelDTO) => Observable<never>;
    channelActions$ = new EventEmitter<ChannelAction>();

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);
    private layoutService: LayoutService = inject(LayoutService);

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
        this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);

        this.breakpointSubscription = this.layoutService.subscribeToLayoutChanges().subscribe(() => {
            this.isMobile = this.layoutService.isBreakpointActive(CustomBreakpointNames.extraSmall);
        });

        this.openSidebarEventSubscription = this.courseSidebarService.openSidebar$.subscribe(() => {
            this.setIsCollapsed(true);
        });

        this.closeSidebarEventSubscription = this.courseSidebarService.closeSidebar$.subscribe(() => {
            this.setIsCollapsed(false);
        });

        this.toggleSidebarEventSubscription = this.courseSidebarService.toggleSidebar$.subscribe(() => {
            this.toggleSidebar();
        });

        if (!this.isMobile) {
            if (this.courseOverviewService.getSidebarCollapseStateFromStorage('conversation')) {
                this.courseSidebarService.openSidebar();
            } else {
                this.courseSidebarService.closeSidebar();
            }
        } else {
            this.courseSidebarService.openSidebar();
        }

        this.isLoading = true;
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
            this.channelActions$
                .pipe(
                    debounceTime(500),
                    distinctUntilChanged((prev, curr) => prev.action === curr.action && prev.channel.id === curr.channel.id),
                    takeUntil(this.ngUnsubscribe),
                )
                .subscribe((channelAction) => {
                    this.performChannelAction(channelAction);
                });
            this.createChannelFn = (channel: ChannelDTO) => this.metisConversationService.createChannel(channel);
        });

        this.profileSubscription = this.profileService.getProfileInfo()?.subscribe((profileInfo) => {
            this.isProduction = profileInfo?.inProduction;
            this.isTestServer = profileInfo.testServer ?? false;
        });
    }

    performChannelAction(channelAction: ChannelAction) {
        if (this.createChannelFn) {
            this.createChannelFn(channelAction.channel)
                .pipe(takeUntil(this.ngUnsubscribe))
                .subscribe({
                    complete: () => {
                        this.prepareSidebarData();
                    },
                    error: (error) => {
                        console.error('Error creating channel:', error);
                    },
                });
        }
    }

    subscribeToQueryParameter() {
        this.activatedRoute.queryParams.pipe(take(1), takeUntil(this.ngUnsubscribe)).subscribe((queryParams) => {
            if (queryParams.conversationId) {
                this.metisConversationService.setActiveConversation(Number(queryParams.conversationId));

                this.closeSidebarOnMobile();
            }
            if (queryParams.messageId) {
                this.postInThread = { id: Number(queryParams.messageId) } as Post;

                this.closeSidebarOnMobile();
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
        this.openSidebarEventSubscription?.unsubscribe();
        this.closeSidebarEventSubscription?.unsubscribe();
        this.toggleSidebarEventSubscription?.unsubscribe();
        this.profileSubscription?.unsubscribe();
        this.breakpointSubscription?.unsubscribe();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversation: ConversationDTO) => {
            const previousConversation = this.activeConversation;
            this.activeConversation = conversation;
            if (this.isMobile && conversation && previousConversation?.id !== conversation.id) {
                this.courseSidebarService.closeSidebar();
            }
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

    hideSearchTerm() {
        this.courseWideSearchTerm = '';
    }

    onSearch() {
        if (this.isMobile) {
            if (this.courseWideSearchTerm) {
                this.courseSidebarService.closeSidebar();
            } else {
                this.courseSidebarService.openSidebar();
            }
        }
        this.metisConversationService.setActiveConversation(undefined);
        this.activeConversation = undefined;
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
            showAccordionLeadingIcon: true,
            messagingEnabled: isMessagingEnabled(this.course),
        };
    }

    onConversationSelected(conversationId: number) {
        this.closeSidebarOnMobile();
        this.metisConversationService.setActiveConversation(conversationId);
    }

    toggleSidebar() {
        this.setIsCollapsed(!this.isCollapsed);
    }

    closeSidebarOnMobile() {
        if (this.isMobile) {
            this.courseSidebarService.closeSidebar();
        }
    }

    setIsCollapsed(value: boolean) {
        this.isCollapsed = value;
        this.courseOverviewService.setSidebarCollapseState('conversation', this.isCollapsed);
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

    openCreateChannelDialog() {
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe((channel: ChannelDTO) => {
                this.channelActions$.emit({ action: 'create', channel });
            });
    }

    openChannelOverviewDialog() {
        const subType = null;
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
                                this.closeSidebarOnMobile();
                            }
                        },
                    });
                } else {
                    if (newActiveConversation) {
                        this.metisConversationService.setActiveConversation(newActiveConversation);
                        this.closeSidebarOnMobile();
                    }
                }
                this.prepareSidebarData();
            });
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

import { AfterViewInit, Component, OnInit } from '@angular/core';
import interact from 'interactjs';
import { faChevronLeft, faChevronRight, faComments, faGripLinesVertical, faPlus } from '@fortawesome/free-solid-svg-icons';

import { from, Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Conversation, ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-conversations/dialogs/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelsCreateDialogComponent } from 'app/overview/course-conversations/dialogs/channels-create-dialog/channels-create-dialog.component';
import { Channel, ChannelDTO, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, GroupChatDto, isGroupChatDto } from 'app/entities/metis/conversation/groupChat.model';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';

@Component({
    selector: 'jhi-conversation-selection-sidebar',
    styleUrls: ['./conversation-selection-sidebar.component.scss'],
    templateUrl: './conversation-selection-sidebar.component.html',
})
export class ConversationSelectionSidebarComponent implements AfterViewInit, OnInit {
    course?: Course;
    activeConversation?: ConversationDto;
    allConversations: ConversationDto[] = [];
    starredConversations: ConversationDto[] = [];
    channelConversations: ChannelDTO[] = [];
    groupChats: GroupChatDto[] = [];

    collapsed: boolean;
    isLoading = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;

    // Icons
    faChevronLeft = faChevronLeft;
    faChevronRight = faChevronRight;
    faGripLinesVertical = faGripLinesVertical;
    faConversation = faComments;
    faPlus = faPlus;

    constructor(
        private courseManagementService: CourseManagementService,
        private modalService: NgbModal,
        // instantiated at course-conversation.component.ts
        public metisConversationService: MetisConversationService,
    ) {}

    ngOnInit(): void {
        this.course = this.metisConversationService.course;
        this.subscribeToActiveConversation();
        this.subscribeToConversationsOfUser();
    }

    private subscribeToActiveConversation() {
        this.metisConversationService.activeConversation$.subscribe((conversation: ConversationDto) => {
            this.activeConversation = conversation;
        });
    }

    private subscribeToConversationsOfUser() {
        this.metisConversationService.conversationsOfUser$.subscribe((conversations: ConversationDto[]) => {
            this.onConversationsUpdate(conversations);
        });
    }

    onConversationsUpdate(conversations: ConversationDto[]) {
        this.allConversations = conversations ?? [];
        this.channelConversations = this.allConversations
            .filter((conversation) => isChannelDto(conversation))
            .map((channel) => channel as Channel)
            .sort((a, b) => a.name!.localeCompare(b.name!));
        this.groupChats = this.allConversations
            .filter((conversation) => isGroupChatDto(conversation))
            .map((groupChat) => groupChat as GroupChat)
            .sort((a, b) => {
                // sort by last message date
                const aLastMessageDate = a.lastMessageDate ? a.lastMessageDate : a.creationDate;
                const bLastMessageDate = b.lastMessageDate ? b.lastMessageDate : b.creationDate;
                // newest messages at the top of the list
                return bLastMessageDate!.isAfter(aLastMessageDate!) ? 1 : -1;
            });
    }

    ngAfterViewInit(): void {
        // allows the conversation sidebar to be resized towards the right-hand side
        interact('.expanded-conversations')
            .resizable({
                edges: { left: false, right: '.draggable-right', bottom: false, top: false },
                modifiers: [
                    // Set maximum width of the conversation sidebar
                    interact.modifiers!.restrictSize({
                        min: { width: 230, height: 0 },
                        max: { width: 500, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * Receives the search text, modifies them and returns the result which will be used by course messages.
     *
     * 1. Perform server-side search using the search text
     * 2. Return results from server query that contain other users of course
     *
     * @param stream$ stream of searches of the format {text, entities} where entities are the results
     * @return stream of users for the autocomplete
     */
    searchUsersWithinCourse = (stream$: Observable<{ text: string; entities: User[] }>): Observable<User[]> => {
        return stream$.pipe(
            switchMap(({ text: loginOrName }) => {
                this.searchFailed = false;
                this.searchNoResults = false;
                if (loginOrName.length < 3) {
                    return of([]);
                }
                this.isSearching = true;
                return this.courseManagementService
                    .searchOtherUsersInCourse(this.course?.id!, loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        tap((users) => {
                            if (users.length === 0) {
                                this.searchNoResults = true;
                            }
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => {
                this.isSearching = false;
            }),
        );
    };

    openCreateChannelDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsCreateDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe((channelToCreate: ChannelDTO) => {
            this.metisConversationService.createNewConversation(channelToCreate).subscribe({
                complete: () => {
                    this.metisConversationService.forceRefresh().subscribe(() => {});
                },
            });
        });
    }

    openChannelOverviewDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.createChannelFn = this.metisConversationService.createNewConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result).subscribe((newActiveConversation: ConversationDto) => {
            this.metisConversationService.forceRefresh().subscribe(() => {
                if (newActiveConversation) {
                    this.metisConversationService.setActiveConversation(newActiveConversation);
                }
            });
        });
    }

    /**t
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     *
     * @param user The selected user from the autocomplete suggestions
     */
    onAutocompleteSelect = (user: User): void => {
        // ToDo: here müssen wir die find logik ändern wenn es gruppengespräche mit mehr nutzern gibt. Vielleiht id aus allen mitgliedern oder so?
        // vielleicht die ids der mitglieder sortieren und dann als string nehmen
        const foundConversation = undefined;
        // if a conversation does not already exist with selected user
        if (foundConversation === undefined) {
            const newConversation = this.createNewGroupChatWithUser(user);
            // this.createConversation(newConversation);
        } else {
            // conversation with the found user already exists, so we select it --> Logik funktioniert aber nicht mehr sobald mehr als 2 user in einem gruppengespräch sind
            this.metisConversationService.setActiveConversation(foundConversation);
        }
    };

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        return `${user.name}`;
    };

    clearUserSearchBar = () => {
        return '';
    };

    // ToDo: hier müssen wir die find logik ändern wenn es gruppengespräche mit mehr nutzern gibt. Vielleiht id aus allen mitgliedern oder so?
    // findGroupChatWithUser(user: User) {
    //     return this.directConversations.find((conversation) => conversation.conversationParticipants!.some((participant) => participant.user.id === user.id));
    // }

    createNewGroupChatWithUser(user: User) {
        const groupChat = new GroupChat();
        groupChat.course = this.course!;
        groupChat.conversationParticipants = [this.createNewConversationParticipant(user)];

        return groupChat;
    }

    createNewConversationParticipant(user: User) {
        const conversationParticipant = new ConversationParticipant();
        conversationParticipant.user = user;
        return conversationParticipant;
    }

    onConversationSelected($event: ConversationDto) {
        this.metisConversationService.setActiveConversation($event);
    }
}

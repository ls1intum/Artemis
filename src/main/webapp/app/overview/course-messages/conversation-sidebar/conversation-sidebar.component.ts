import { AfterViewInit, Component, EventEmitter, Input, Output } from '@angular/core';
import interact from 'interactjs';
import { ActivatedRoute } from '@angular/router';
import { faChevronLeft, faChevronRight, faComments, faGripLinesVertical, faPlus } from '@fortawesome/free-solid-svg-icons';

import { from, Observable, of, Subject } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-messages/channels/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelsCreateDialogComponent } from 'app/overview/course-messages/channels/channels-create-dialog/channels-create-dialog.component';
import { Channel, isChannel } from 'app/entities/metis/conversation/channel.model';
import { GroupChat, isGroupChat } from 'app/entities/metis/conversation/groupChat.model';

@Component({
    selector: 'jhi-conversation-sidebar',
    styleUrls: ['./conversation-sidebar.component.scss'],
    templateUrl: './conversation-sidebar.component.html',
})
export class ConversationSidebarComponent implements AfterViewInit {
    @Input()
    refreshConversations$ = new Subject<void>();

    @Input()
    course?: Course;

    @Output() conversationSelected = new EventEmitter<Conversation | undefined>();

    @Output() channelOverViewModalResult = new EventEmitter<number | number[]>();

    @Output() newConversationCreated = new EventEmitter<Conversation>();

    @Input()
    set conversations(conversations: Conversation[]) {
        this.allConversations = conversations ?? [];
        this.channelConversations = this.allConversations
            .filter((conversation) => isChannel(conversation))
            .map((channel) => channel as Channel)
            .sort((a, b) => a.name!.localeCompare(b.name!));
        this.directConversations = this.allConversations.filter((conversation) => isGroupChat(conversation)).map((groupChat) => groupChat as GroupChat);
    }

    @Input()
    activeConversation?: Conversation;

    allConversations: Conversation[] = [];
    starredConversations: Conversation[] = [];
    channelConversations: Channel[] = [];
    directConversations: GroupChat[] = [];

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

    constructor(private courseManagementService: CourseManagementService, private activatedRoute: ActivatedRoute, private modalService: NgbModal) {}

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

        from(modalRef.result).subscribe((channel: Channel) => {
            if (channel) {
                this.createConversation(channel);
            }
        });
    }

    openChannelOverviewDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ChannelsOverviewDialogComponent, { size: 'lg', scrollable: false, backdrop: 'static' });
        modalRef.componentInstance.courseId = this.course?.id!;

        from(modalRef.result).subscribe((result: number[] | number) => {
            this.channelOverViewModalResult.emit(result);
        });
    }

    /**t
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     *
     * @param user The selected user from the autocomplete suggestions
     */
    onAutocompleteSelect = (user: User): void => {
        // ToDo: here müssen wir die find logik ändern wenn es gruppengespräche mit mehr nutzern gibt. Vielleiht id aus allen mitgliedern oder so?
        const foundConversation = this.findGroupChatWithUser(user);
        // if a conversation does not already exist with selected user
        if (foundConversation === undefined) {
            const newConversation = this.createNewGroupChatWithUser(user);
            this.createConversation(newConversation);
        } else {
            // conversation with the found user already exists, so we select it
            this.conversationSelected.emit(foundConversation);
        }
    };

    private createConversation(newConversation: Conversation) {
        this.newConversationCreated.emit(newConversation);
    }
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

    findGroupChatWithUser(user: User) {
        return this.directConversations.find((conversation) => conversation.conversationParticipants!.some((participant) => participant.user.id === user.id));
    }

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

    onConversationSelected($event: Conversation) {
        this.conversationSelected.emit($event);
    }
}

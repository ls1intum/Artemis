import { AfterViewInit, Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import interact from 'interactjs';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { faChevronLeft, faChevronRight, faComments, faGripLinesVertical, faPlus } from '@fortawesome/free-solid-svg-icons';

import { MessagingService } from 'app/shared/metis/messaging.service';
import { from, Observable, of, Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-participant.model';
import { ConversationType } from 'app/shared/metis/metis.util';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ChannelsOverviewDialogComponent } from 'app/overview/course-messages/channels/channels-overview-dialog/channels-overview-dialog.component';
import { ChannelsCreateDialogComponent } from 'app/overview/course-messages/channels/channels-create-dialog/channels-create-dialog.component';

@Component({
    selector: 'jhi-conversation-sidebar',
    styleUrls: ['./conversation-sidebar.component.scss'],
    templateUrl: './conversation-sidebar.component.html',
    providers: [MessagingService],
})
export class ConversationSidebarComponent implements OnInit, AfterViewInit, OnDestroy {
    @Input()
    course?: Course;

    @Output() selectConversation = new EventEmitter<Conversation>();

    conversations: Conversation[];

    starredConversations: Conversation[] = [];
    channelConversations: Conversation[] = [];
    directConversations: Conversation[] = [];

    activeConversation?: Conversation;

    collapsed: boolean;

    private conversationSubscription: Subscription;

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
        protected courseMessagesService: MessagingService,
        private courseManagementService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
        private modalService: NgbModal,
    ) {}

    ngOnInit(): void {
        if (this.course) {
            this.courseManagementService.findOneForDashboard(this.course.id!).subscribe((res: HttpResponse<Course>) => {
                if (res.body !== undefined) {
                    this.course = res.body!;
                }
            });
            this.courseMessagesService.getConversationsOfUser(this.course.id!).subscribe();
            this.conversationSubscription = this.courseMessagesService.conversations.subscribe((conversations: Conversation[]) => {
                this.conversations = conversations ?? [];
                this.channelConversations = this.conversations
                    .filter((conversation) => conversation.type === ConversationType.CHANNEL)
                    .sort((a, b) => a.name!.localeCompare(b.name!));
                this.directConversations = this.conversations.filter((conversation) => conversation.type === ConversationType.DIRECT);

                // ToDo: Select starred conversations here

                if (this.conversations.length > 0 && !this.activeConversation) {
                    // emit the value to fetch conversation posts on post overview tab
                    // ToDo: Überlegen welche conversation hier ausgewählt werden soll
                    this.activeConversation = this.conversations.first()!;
                    this.selectConversation.emit(this.activeConversation);
                }
            });
        }
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

        from(modalRef.result).subscribe((channel: Conversation) => {
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
            this.courseMessagesService.getConversationsOfUser(this.course?.id!).subscribe(() => {
                if (Array.isArray(result)) {
                    // result represents array of ids of conversations that were unsubscribed
                    if (this.activeConversation && result.includes(this.activeConversation.id!)) {
                        this.activeConversation = undefined;
                    }
                } else {
                    // result represent id of conversation that should be viewed
                    if (this.activeConversation && result !== this.activeConversation.id) {
                        this.activeConversation = this.conversations.find((conversation) => conversation.id === result);
                        this.selectConversation.emit(this.activeConversation);
                    }
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
        const foundConversation = this.findConversationWithUser(user);
        // if a conversation does not already exist with selected user
        if (foundConversation === undefined) {
            const newConversation = this.createNewConversationWithUser(user);
            this.createConversation(newConversation);
        } else {
            // conversation with the found user already exists, so we select it
            this.activeConversation = foundConversation;
            this.selectConversation.emit(foundConversation);
        }
    };

    private createConversation(newConversation: Conversation) {
        this.isTransitioning = true;
        this.courseMessagesService.createConversation(this.course?.id!, newConversation).subscribe({
            next: (conversation: Conversation) => {
                this.isTransitioning = false;

                // select the new conversation
                this.activeConversation = conversation;
                this.selectConversation.emit(conversation);
            },
            error: () => {
                this.isTransitioning = false;
            },
        });
    }

    ngOnDestroy(): void {
        this.conversationSubscription?.unsubscribe();
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

    findConversationWithUser(user: User) {
        return this.conversations.find((conversation) => conversation.conversationParticipants!.some((conversationParticipant) => conversationParticipant.user.id === user.id));
    }

    createNewConversationWithUser(user: User) {
        const conversation = new Conversation();
        conversation.type = ConversationType.DIRECT;
        conversation.course = this.course!;
        conversation.conversationParticipants = [this.createNewConversationParticipant(user)];

        return conversation;
    }

    createNewConversationParticipant(user: User) {
        const conversationParticipant = new ConversationParticipant();
        conversationParticipant.user = user;
        return conversationParticipant;
    }

    onConversationSelected($event: Conversation) {
        this.activeConversation = $event;
        this.selectConversation.emit($event);
    }
}

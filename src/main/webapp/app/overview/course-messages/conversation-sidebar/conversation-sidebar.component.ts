import { AfterViewInit, Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import interact from 'interactjs';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { faChevronLeft, faChevronRight, faComments, faGripLinesVertical, faMessage } from '@fortawesome/free-solid-svg-icons';

import { CourseMessagesService } from 'app/shared/metis/course.messages.service';
import { combineLatest, Observable, of, Subscription } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ConversationParticipant } from 'app/entities/metis/conversation/conversation-details.model';

@Component({
    selector: 'jhi-conversation-sidebar',
    styleUrls: ['./conversation-sidebar.component.scss'],
    templateUrl: './conversation-sidebar.component.html',
    providers: [CourseMessagesService],
})
export class ConversationSidebarComponent implements OnInit, AfterViewInit, OnDestroy {
    @Output() selectConversation = new EventEmitter<Conversation>();

    conversations: Conversation[];
    activeConversation: Conversation;

    course?: Course;
    collapsed: boolean;
    courseId: number;

    private conversationSubscription: Subscription;
    private paramSubscription: Subscription;

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
    faMessage = faMessage;

    constructor(protected courseMessagesService: CourseMessagesService, private courseManagementService: CourseManagementService, private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {
        this.paramSubscription = combineLatest({
            params: this.activatedRoute.parent!.parent!.params,
            queryParams: this.activatedRoute.parent!.parent!.queryParams,
        }).subscribe((routeParams: { params: Params; queryParams: Params }) => {
            const { params } = routeParams;
            this.courseId = params.courseId;
            this.courseManagementService.findOneForDashboard(this.courseId).subscribe((res: HttpResponse<Course>) => {
                if (res.body !== undefined) {
                    this.course = res.body!;
                }
            });
            this.courseMessagesService.getConversationsOfUser(this.courseId);
            this.conversationSubscription = this.courseMessagesService.conversations.subscribe((conversations: Conversation[]) => {
                this.conversations = conversations;
                if (this.conversations.length > 0 && !this.activeConversation) {
                    // emit the value to fetch conversation posts on post overview tab
                    this.activeConversation = this.conversations.first()!;
                    this.selectConversation.emit(this.activeConversation);
                }
            });
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
                    .searchOtherUsersInCourse(this.courseId, loginOrName)
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

    /**
     * Receives the user that was selected in the autocomplete and the callback from DataTableComponent.
     *
     * @param user The selected user from the autocomplete suggestions
     */
    onAutocompleteSelect = (user: User): void => {
        const foundConversation = this.findConversationWithUser(user);
        // if a conversation does not already exist with selected user
        if (foundConversation === undefined) {
            const newConversation = this.createNewConversationWithUser(user);
            this.isTransitioning = true;
            this.courseMessagesService.createConversation(this.courseId, newConversation).subscribe({
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
        } else {
            // conversation with the found user already exists, so we select it
            this.activeConversation = foundConversation;
            this.selectConversation.emit(foundConversation);
        }
    };

    ngOnDestroy(): void {
        this.conversationSubscription?.unsubscribe();
    }

    /**
     * defines a function that returns the conversation id as unique identifier,
     * by this means, Angular determines which conversation in the collection of conversation has to be reloaded/destroyed on changes
     */
    conversationsTrackByFn = (index: number, conversation: Conversation): number => conversation.id!;

    getNameOfConversationParticipant(conversation: Conversation): string {
        const participant = conversation.conversationParticipants!.find((conversationParticipants) => conversationParticipants.user.id !== this.courseMessagesService.userId)!.user;
        return participant.firstName!;
    }

    isConversationUnread(conversation: Conversation): boolean {
        const conversationParticipant = conversation.conversationParticipants!.find(
            (conversationParticipants) => conversationParticipants.user.id === this.courseMessagesService.userId,
        )!;

        if (conversation.id !== this.activeConversation.id && !!conversation.lastMessageDate && !!conversationParticipant.lastRead) {
            if (conversation.lastMessageDate > conversationParticipant.lastRead) {
                return true;
            }
        }
        return false;
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
        conversation.course = this.course!;
        conversation.conversationParticipants = [this.createNewConversationParticipant(user)];

        return conversation;
    }

    createNewConversationParticipant(user: User) {
        const conversationParticipant = new ConversationParticipant();
        conversationParticipant.user = user;
        return conversationParticipant;
    }
}

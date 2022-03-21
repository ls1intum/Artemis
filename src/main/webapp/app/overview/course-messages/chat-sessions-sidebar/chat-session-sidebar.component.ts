import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, SimpleChanges } from '@angular/core';
import { ActivatedRoute, Params } from '@angular/router';
import { HttpResponse } from '@angular/common/http';

import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { faArrowLeft, faArrowRight, faChevronRight, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

import { ChatSessionService } from 'app/shared/metis/chat-session.service';
import { combineLatest, Observable, of, Subscription } from 'rxjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { ChatSession } from 'app/entities/metis/chat.session/chat-session.model';
import { User } from 'app/core/user/user.model';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { UserChatSession } from 'app/entities/metis/chat.session/user-chat-session.model';
import { Course } from 'app/entities/course.model';
import { ChatService } from 'app/shared/metis/chat.service';

@Component({
    selector: 'jhi-chat-session-sidebar',
    styleUrls: ['./chat-session-sidebar.component.scss'],
    templateUrl: './chat-session-sidebar.component.html',
    providers: [MetisService, ChatSessionService],
})
export class ChatSessionSidebarComponent implements OnInit, OnDestroy, OnChanges {
    @Input() activeID: number;
    @Input() comparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];
    @Input() casesFiltered = false;
    @Input() offset = 0;

    @Input() showRunDetails: boolean;
    @Output() showRunDetailsChange = new EventEmitter<boolean>();

    @Output() selectIndex = new EventEmitter<number>();

    faExclamationTriangle = faExclamationTriangle;

    chatSessions: ChatSession[];

    /**
     * Index of the currently selected result page.
     */
    public currentPage = 0;

    /**
     * Total number of result pages.
     */
    public numberOfPages = 0;

    /**
     * Subset of currently paged comparisons.
     */
    public pagedComparisons?: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[];

    /**
     * Number of comparisons per page.
     */
    public pageSize = 100;

    course?: Course;
    courseId: number;

    private chatSessionSubscription: Subscription;
    private paramSubscription: Subscription;

    isAdmin = false;
    isLoading = false;
    isSearching = false;
    searchFailed = false;
    searchNoResults = false;
    isTransitioning = false;
    rowClass: string | undefined = undefined;

    // Icons
    faChevronRight = faChevronRight;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor(
        protected metisService: MetisService,
        protected chatService: ChatService,
        private courseManagementService: CourseManagementService,
        private activatedRoute: ActivatedRoute,
    ) {}

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
            this.chatService.getChatSessionsOfUser(this.courseId);
            this.chatSessionSubscription = this.chatService.chatSessions.pipe().subscribe((chatSessions: ChatSession[]) => {
                this.chatSessions = chatSessions;
            });
        });
    }

    /**
     * Receives the search text and filter results from DataTableComponent, modifies them and returns the result which will be used by ngbTypeahead.
     *
     * 1. Perform server-side search using the search text
     * 2. Return results from server query that contain all users (instead of only the client-side users who are group members already)
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
     * The callback inserts the search term of the selected entity into the search field and updates the displayed users.
     *
     * @param user The selected user from the autocomplete suggestions
     * @param callback Function that can be called with the selected user to trigger the DataTableComponent default behavior
     */
    onAutocompleteSelect = (user: User, callback: (user: User) => void): void => {
        // If the user is not part of this course group yet, perform the server call to add them

        // const index = this.findIndexOfChatSessionWithUser(user);
        if (true) {
            const newChatSession = this.prepareNewChatSessionWithUser(user);
            this.isTransitioning = true;
            this.chatService.createChatSession(this.courseId, newChatSession).subscribe({
                next: (chatSession: ChatSession) => {
                    this.isTransitioning = false;

                    // Add newly added user to the list of all users in the course group
                    this.chatSessions.push(chatSession);

                    // Hand back over to the data table for updating
                    callback(user);
                },
                error: () => {
                    this.isTransitioning = false;
                },
            });
        } else {
            // Hand back over to the data table
            callback(user);
        }
    };

    ngOnDestroy(): void {
        this.chatSessionSubscription?.unsubscribe();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.comparisons) {
            const comparisons: PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>[] = changes.comparisons.currentValue;

            this.currentPage = 0;
            this.numberOfPages = this.computeNumberOfPages(comparisons.length);
            this.pagedComparisons = this.getPagedComparisons();
        }
    }

    displayRunDetails() {
        this.showRunDetailsChange.emit(true);
    }

    computeNumberOfPages(totalComparisons: number) {
        return Math.floor(totalComparisons / this.pageSize);
    }

    getPagedComparisons() {
        const startIndex = this.currentPage * this.pageSize;

        return this.comparisons?.slice(startIndex, startIndex + this.pageSize);
    }

    getPagedIndex(idx: number) {
        return idx + this.currentPage * this.pageSize;
    }

    handlePageLeft() {
        if (this.currentPage === 0) {
            return;
        }

        this.currentPage--;
        this.pagedComparisons = this.getPagedComparisons();
    }

    handlePageRight() {
        if (this.currentPage === this.numberOfPages) {
            return;
        }

        this.currentPage++;
        this.pagedComparisons = this.getPagedComparisons();
    }

    getNameOfChatSessionParticipant(chatSession: ChatSession): string {
        const participant = chatSession.userChatSessions.find((userChatSession) => userChatSession.user.id !== this.metisService.getUser().id)!.user;

        return participant.firstName!;
    }

    /**
     * Formats the results in the autocomplete overlay.
     *
     * @param user
     */
    searchResultFormatter = (user: User) => {
        const { name } = user;
        return `${name}`;
    };

    findIndexOfChatSessionWithUser(user: User) {
        const chatSessionIndex = this.chatSessions.find((chatSession) => {
            chatSession.userChatSessions.find((userChatSession) => userChatSession.user.id === user.id);
        });

        return chatSessionIndex;
    }

    prepareNewChatSessionWithUser(user: User) {
        const chatSession = new ChatSession();
        chatSession.course = this.course!;
        chatSession.userChatSessions = [this.prepareUserChatSession(user)];

        return chatSession;
    }

    prepareUserChatSession(user: User) {
        const userChatSession = new UserChatSession();
        userChatSession.user = user;
        return userChatSession;
    }
}

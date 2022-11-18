import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { ConversationMemberSearchFilter, ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';
import { Course } from 'app/entities/course.model';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { Subject, from, map } from 'rxjs';
import { faMagnifyingGlass, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ConversationAddUsersDialogComponent } from 'app/overview/course-conversations/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';
import { getAsChannelDto, isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { canAddUsersToConversation } from 'app/shared/metis/conversations/conversation-permissions.utils';
import { ConversationUserDTO } from 'app/entities/metis/conversation/conversation-user-dto.model';

interface SearchQuery {
    searchTerm: string;
    force: boolean;
}
@Component({
    selector: 'jhi-conversation-members',
    templateUrl: './conversation-members.component.html',
})
export class ConversationMembers implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    private readonly search$ = new Subject<SearchQuery>();
    @Input()
    course: Course;
    @Input()
    public activeConversation: ConversationDto;
    @Output()
    changesPerformed = new EventEmitter<void>();
    canAddUsersToConversation = canAddUsersToConversation;
    getAsChannel = getAsChannelDto;
    isChannel = isChannelDto;

    members: ConversationUserDTO[] = [];
    // page information
    page = 1;
    itemsPerPage = 10;
    totalItems = 0;
    isSearching = true;
    searchTerm = '';

    // icons
    faMagnifyingGlass = faMagnifyingGlass;
    faUserPlus = faUserPlus;

    selectedFilter: ConversationMemberSearchFilter = ConversationMemberSearchFilter.ALL;

    ALL = ConversationMemberSearchFilter.ALL;
    INSTRUCTOR_FILTER_OPTION = ConversationMemberSearchFilter.INSTRUCTOR;
    EDITOR_FILTER_OPTION = ConversationMemberSearchFilter.EDITOR;
    TUTOR_FILTER_OPTION = ConversationMemberSearchFilter.TUTOR;
    STUDENT_FILTER_OPTION = ConversationMemberSearchFilter.STUDENT;
    CHANNEL_ADMIN_FILTER_OPTION = ConversationMemberSearchFilter.CHANNEL_ADMIN;

    constructor(public conversationService: ConversationService, private alertService: AlertService, private modalService: NgbModal) {}

    trackIdentity(index: number, item: ConversationUserDTO) {
        return item.id;
    }

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationAddUsersDialogComponent, {
            size: 'lg',
            scrollable: false,
            backdrop: 'static',
            backdropClass: 'second-layer-modal-bg',
            centered: true,
        });
        modalRef.componentInstance.course = this.course;
        modalRef.componentInstance.activeConversation = this.activeConversation;
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(takeUntil(this.ngUnsubscribe))
            .subscribe(() => {
                this.onChangePerformed();
            });
    }

    onChangePerformed() {
        this.search$.next({
            searchTerm: this.searchTerm,
            force: true,
        });
        this.changesPerformed.emit();
    }

    ngOnInit(): void {
        this.search$
            .pipe(
                debounceTime(300),
                distinctUntilChanged((prev, curr) => {
                    if (curr.force === true) {
                        return false;
                    } else {
                        return prev === curr;
                    }
                }),
                tap(() => (this.members = [])),
                map((query) => {
                    const searchTerm = query.searchTerm !== null && query.searchTerm !== undefined ? query.searchTerm : '';
                    return searchTerm.trim().toLowerCase();
                }),
                tap((searchTerm) => {
                    this.isSearching = true;
                    this.searchTerm = searchTerm;
                }),
                switchMap(() =>
                    this.conversationService.searchMembersOfConversation(
                        this.course.id!,
                        this.activeConversation.id!,
                        this.searchTerm,
                        this.page - 1,
                        this.itemsPerPage,
                        this.selectedFilter,
                    ),
                ),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: (res: HttpResponse<ConversationUserDTO[]>) => {
                    this.isSearching = false;
                    this.onSuccess(res.body, res.headers);
                },
                error: (errorResponse: HttpErrorResponse) => {
                    this.isSearching = false;
                    onError(this.alertService, errorResponse);
                },
            });
        this.search$.next({
            searchTerm: '',
            force: true,
        });
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onFilterChange(newFilterValue: ConversationMemberSearchFilter) {
        this.selectedFilter = newFilterValue;
        this.search$.next({
            searchTerm: this.searchTerm,
            force: true,
        });
    }

    transition() {
        this.search$.next({
            searchTerm: this.searchTerm,
            force: true,
        });
    }

    onSearchQueryInput($event: Event) {
        const searchTerm = ($event.target as HTMLInputElement).value?.trim().toLowerCase() ?? '';
        this.search$.next({
            searchTerm,
            force: false,
        });
    }

    private onSuccess(members: ConversationUserDTO[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        this.members = members || [];
    }
}

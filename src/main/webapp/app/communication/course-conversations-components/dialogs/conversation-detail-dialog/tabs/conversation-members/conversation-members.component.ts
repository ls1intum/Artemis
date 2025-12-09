import { ChangeDetectorRef, Component, OnDestroy, OnInit, inject, input, output, signal } from '@angular/core';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { catchError, debounceTime, distinctUntilChanged, switchMap, takeUntil, tap } from 'rxjs/operators';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { EMPTY, Subject, from, map } from 'rxjs';
import { faMagnifyingGlass, faUserPlus } from '@fortawesome/free-solid-svg-icons';
import { NgbModal, NgbModalRef, NgbPagination } from '@ng-bootstrap/ng-bootstrap';
import { getAsChannelDTO, isChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationUserDTO } from 'app/communication/shared/entities/conversation/conversation-user-dto.model';
import { defaultSecondLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { ConversationMemberRowComponent } from './conversation-member-row/conversation-member-row.component';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { canAddUsersToConversation } from 'app/communication/conversations/conversation-permissions.utils';
import { ConversationMemberSearchFilter, ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ConversationAddUsersDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-add-users-dialog/conversation-add-users-dialog.component';

interface SearchQuery {
    searchTerm: string;
    force: boolean;
}
@Component({
    selector: 'jhi-conversation-members',
    templateUrl: './conversation-members.component.html',
    imports: [FaIconComponent, TranslateDirective, FormsModule, ConversationMemberRowComponent, ItemCountComponent, NgbPagination, ArtemisTranslatePipe],
})
export class ConversationMembersComponent implements OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    private readonly search$ = new Subject<SearchQuery>();

    course = input.required<Course>();
    activeConversationInput = input.required<ConversationDTO>();
    activeConversation = signal<ConversationDTO | undefined>(undefined);
    changesPerformed = output<void>();
    readonly userNameClicked = output<number>();

    canAddUsersToConversation = canAddUsersToConversation;
    getAsChannel = getAsChannelDTO;
    isChannel = isChannelDTO;

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

    // note: tutors searches for Editors and Tutors
    TUTOR_FILTER_OPTION = ConversationMemberSearchFilter.TUTOR;
    STUDENT_FILTER_OPTION = ConversationMemberSearchFilter.STUDENT;
    CHANNEL_MODERATOR_FILTER_OPTION = ConversationMemberSearchFilter.CHANNEL_MODERATOR;

    public conversationService = inject(ConversationService);
    private alertService = inject(AlertService);
    private modalService = inject(NgbModal);
    private cdr = inject(ChangeDetectorRef);

    trackIdentity(index: number, item: ConversationUserDTO) {
        return item.id;
    }

    openAddUsersDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(ConversationAddUsersDialogComponent, defaultSecondLayerDialogOptions);
        modalRef.componentInstance.course = this.course();
        modalRef.componentInstance.activeConversation = this.activeConversation();
        modalRef.componentInstance.initialize();
        from(modalRef.result)
            .pipe(
                catchError(() => EMPTY),
                takeUntil(this.ngUnsubscribe),
            )
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
                switchMap(() => {
                    if (this.course()?.id && this.activeConversation()?.id) {
                        return this.conversationService.searchMembersOfConversation(
                            this.course().id!,
                            this.activeConversation()!.id!,
                            this.searchTerm,
                            this.page - 1,
                            this.itemsPerPage,
                            Number(this.selectedFilter),
                        );
                    } else {
                        return EMPTY;
                    }
                }),
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

        const inputValue = this.activeConversationInput();
        if (inputValue) {
            this.activeConversation.set(inputValue);
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    onFilterChange(newFilterValue: ConversationMemberSearchFilter) {
        this.selectedFilter = newFilterValue;
        this.page = 1;
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
        this.page = 1;
        const searchTerm = ($event.target as HTMLInputElement).value?.trim().toLowerCase() ?? '';
        this.search$.next({
            searchTerm,
            force: false,
        });
    }

    private onSuccess(members: ConversationUserDTO[] | null, headers: HttpHeaders): void {
        this.totalItems = Number(headers.get('X-Total-Count'));
        if (this.activeConversation) {
            // might have changed because of user deletion or addition
            this.activeConversation.update((current) => {
                if (current) {
                    return Object.assign({}, current, { numberOfMembers: this.totalItems });
                }
                return current;
            });
        }
        this.members = members || [];
        this.cdr.detectChanges();
    }
}

import { Component, ElementRef, HostBinding, Input, OnDestroy, OnInit, ViewChild, ViewEncapsulation } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, OperatorFunction, Subject, catchError, map, of } from 'rxjs';
import { CourseManagementService, RoleGroup } from 'app/course/manage/course-management.service';
import { debounceTime, distinctUntilChanged, filter, switchMap, takeUntil, tap } from 'rxjs/operators';
import { User, UserPublicInfoDTO } from 'app/core/user/user.model';
import { NgbTypeahead, NgbTypeaheadSelectItemEvent } from '@ng-bootstrap/ng-bootstrap';
import { faX } from '@fortawesome/free-solid-svg-icons';

let selectorId = 0;

/**
 * Generic Input Component for searching and selecting one or more users from a course.
 * - Implements ControlValueAccessor to be used in reactive forms.
 * - Uses ng-bootstrap typeahead to provide a search input.
 * - Uses server side search to efficiently search for users.
 * - Uses a custom formatter to display the user's name and login.
 * - Search requires at least 3 characters.
 * - Always returns an array of users. In single mode the array will contain only one user.
 */
@Component({
    selector: 'jhi-course-users-selector',
    templateUrl: './course-users-selector.component.html',
    styleUrls: ['./course-users-selector.component.scss'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: CourseUsersSelectorComponent,
            multi: true,
        },
    ],
    host: { class: 'course-users-selector' },
    encapsulation: ViewEncapsulation.None,
})
export class CourseUsersSelectorComponent implements ControlValueAccessor, OnInit, OnDestroy {
    private ngUnsubscribe = new Subject<void>();

    @ViewChild('instance', { static: true }) typeAheadInstance: NgbTypeahead;
    @Input() disabled = false;
    @ViewChild('searchInput') searchInput: ElementRef;
    @Input()
    courseId: number;
    @Input()
    @HostBinding('attr.id')
    id = 'users-selector' + selectorId++;
    @Input()
    label?: string;
    @Input()
    rolesToAllowSearchingIn: RoleGroup[] = ['tutors', 'students', 'instructors', 'editors'];
    @Input()
    multiSelect = true;

    @Input()
    showUserList = true;

    searchStudents = true;
    searchTutors = true;
    searchEditors = true;
    searchInstructors = true;

    // icons
    faX = faX;

    selectedUsers: UserPublicInfoDTO[] = [];
    isSearching = false;
    searchFailed = false;

    constructor(private courseManagementService: CourseManagementService) {}

    ngOnInit(): void {
        if (this.rolesToAllowSearchingIn.includes('students')) {
            this.searchStudents = true;
        }
        if (this.rolesToAllowSearchingIn.includes('tutors')) {
            this.searchTutors = true;
        }
        if (this.rolesToAllowSearchingIn.includes('editors')) {
            this.searchEditors = true;
        }
        if (this.rolesToAllowSearchingIn.includes('instructors')) {
            this.searchInstructors = true;
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    usersFormatter = (user: UserPublicInfoDTO) => this.getUserLabel(user);

    trackIdentity(index: number, item: UserPublicInfoDTO) {
        return item.id;
    }

    getUserLabel(user: UserPublicInfoDTO) {
        let label = '';
        if (user.firstName) {
            label += user.firstName + ' ';
        }
        if (user.lastName) {
            label += user.lastName + ' ';
        }
        if (user.login) {
            label += '(' + user.login + ')';
        }
        return label.trim();
    }

    onSelectItem($event: NgbTypeaheadSelectItemEvent<User>) {
        this.onUserSelected($event.item);
        $event.preventDefault();
        this.resetSearchInput();
    }

    onDelete(index: number) {
        this.selectedUsers.splice(index, 1);
        this.onChange(this.selectedUsers);
    }

    onFilterChange() {
        this.typeAheadInstance?.dismissPopup();
        this.searchInput.nativeElement.dispatchEvent(new Event('input'));
    }

    search: OperatorFunction<string, readonly UserPublicInfoDTO[]> = (text$: Observable<string>) =>
        text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            map((term) => (term ? term.trim().toLowerCase() : '')),
            // the three letter minimum is enforced by the server only for searching for students as they are so many
            filter((term) => term.length >= 3 || !this.searchStudents),
            switchMap((term) => {
                const rolesToSearchIn: RoleGroup[] = [];
                if (this.searchStudents) {
                    rolesToSearchIn.push('students');
                }
                if (this.searchTutors) {
                    rolesToSearchIn.push('tutors');
                }
                if (this.searchEditors) {
                    rolesToSearchIn.push('editors');
                }
                if (this.searchInstructors) {
                    rolesToSearchIn.push('instructors');
                }
                if (rolesToSearchIn.length === 0) {
                    this.searchFailed = false;
                    return of([]);
                } else {
                    this.isSearching = true;
                    return this.courseManagementService.searchUsers(this.courseId, term, rolesToSearchIn).pipe(
                        map((users) => users.body!),
                        map((users) => users.filter((user) => !this.selectedUsers.find((selectedUser) => selectedUser.id === user.id))),
                        tap(() => {
                            this.isSearching = false;
                            this.searchFailed = false;
                        }),
                        catchError(() => {
                            this.searchFailed = true;
                            this.isSearching = false;
                            return of([]);
                        }),
                        takeUntil(this.ngUnsubscribe),
                    );
                }
            }),
            takeUntil(this.ngUnsubscribe),
        );

    // === START CONTROL VALUE ACCESSOR ===
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    onChange = (selectedUsers: UserPublicInfoDTO[]) => {};

    onTouched = () => {};

    registerOnChange(fn: (selectedUsers: UserPublicInfoDTO[]) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }

    writeValue(selectedUsers: UserPublicInfoDTO[]): void {
        if (this.multiSelect) {
            this.selectedUsers = selectedUsers ?? [];
        } else {
            this.selectedUsers = selectedUsers?.length ? [selectedUsers[0]] : [];
        }
    }
    // === END CONTROL VALUE ACCESSOR ===

    private onUserSelected(selectedUser: UserPublicInfoDTO) {
        if (selectedUser) {
            if (!this.selectedUsers.find((user) => user.id === selectedUser.id)) {
                if (this.multiSelect) {
                    this.selectedUsers = [...this.selectedUsers, selectedUser];
                } else {
                    this.selectedUsers = [selectedUser];
                }
                this.onChange(this.selectedUsers);
            }
        }
    }

    private resetSearchInput() {
        if (this.searchInput) {
            this.searchInput.nativeElement.value = '';
        }
    }
}

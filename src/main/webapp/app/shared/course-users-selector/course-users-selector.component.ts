import { Component, ElementRef, HostBinding, Input, ViewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { catchError, map, Observable, of, OperatorFunction } from 'rxjs';
import { CourseManagementService, RoleGroup } from 'app/course/manage/course-management.service';
import { debounceTime, distinctUntilChanged, filter, switchMap, tap } from 'rxjs/operators';
import { User } from 'app/core/user/user.model';
import { NgbTypeaheadSelectItemEvent } from '@ng-bootstrap/ng-bootstrap';
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
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: CourseUsersSelectorComponent,
            multi: true,
        },
    ],
})
export class CourseUsersSelectorComponent implements ControlValueAccessor {
    @Input() disabled = false;
    @ViewChild('searchInput') searchInput: ElementRef;
    @Input()
    courseId: number;
    @Input()
    @HostBinding('attr.id')
    id = 'users-selector' + selectorId++;
    @Input()
    label = '';
    @Input()
    rolesToSearch: RoleGroup[] = ['tutors', 'students', 'instructors', 'editors'];
    @Input()
    multiSelect = true;

    // icons
    faX = faX;

    selectedUsers: User[] = [];
    isSearching = false;
    searchFailed = false;

    constructor(private courseManagementService: CourseManagementService) {}

    usersFormatter = (user: User) => this.getUserLabel(user);

    trackIdentity(index: number, item: User) {
        return item.id;
    }

    getUserLabel(user: User) {
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

    search: OperatorFunction<string, readonly User[]> = (text$: Observable<string>) =>
        text$.pipe(
            debounceTime(300),
            distinctUntilChanged(),
            filter((term) => !!term && term.length >= 3),
            map((term) => term.trim().toLowerCase()),
            tap(() => (this.isSearching = true)),
            switchMap((term) =>
                this.courseManagementService.searchUsers(this.courseId, term, this.rolesToSearch).pipe(
                    map((users) => users.body!),
                    map((users) => users.filter((user) => !this.selectedUsers.find((selectedUser) => selectedUser.id === user.id))),
                    tap(() => (this.searchFailed = false)),
                    catchError(() => {
                        this.searchFailed = true;
                        return of([]);
                    }),
                ),
            ),
            tap(() => (this.isSearching = false)),
        );

    // === START CONTROL VALUE ACCESSOR ===
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    onChange = (selectedUsers: User[]) => {};

    onTouched = () => {};

    registerOnChange(fn: (selectedUsers: User[]) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabled = isDisabled;
    }

    writeValue(selectedUsers: User[]): void {
        if (this.multiSelect) {
            this.selectedUsers = selectedUsers ?? [];
        } else {
            this.selectedUsers = selectedUsers?.length ? [selectedUsers[0]] : [];
        }
    }
    // === END CONTROL VALUE ACCESSOR ===

    private onUserSelected(selectedUser: User) {
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

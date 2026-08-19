import { Component, ElementRef, HostBinding, OnDestroy, OnInit, ViewEncapsulation, inject, input, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, OperatorFunction, Subject, catchError, map, of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { debounceTime, distinctUntilChanged, filter, switchMap, takeUntil, tap } from 'rxjs/operators';
import { User, UserPublicInfoDTO } from 'app/account/user/user.model';
import { NgbTypeahead, NgbTypeaheadSelectItemEvent } from '@ng-bootstrap/ng-bootstrap';
import { faX } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { addPublicFilePrefix } from 'app/app.constants';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

let selectorId = 0;

// Note: Searching for tutors also searches for editors
export type SearchRoleGroup = 'tutors' | 'students' | 'instructors';

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
    encapsulation: ViewEncapsulation.None,
    imports: [NgbTypeahead, ProfilePictureComponent, TranslateDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class CourseUsersSelectorComponent implements ControlValueAccessor, OnInit, OnDestroy {
    private courseManagementService = inject(CourseManagementService);

    @HostBinding('class.course-users-selector') hostClass = true;

    private ngUnsubscribe = new Subject<void>();

    readonly typeAheadInstance = viewChild.required<NgbTypeahead>('instance');
    readonly disabled = signal(false);
    readonly searchInput = viewChild.required<ElementRef>('searchInput');
    readonly courseId = input.required<number>();

    @HostBinding('attr.id')
    id = 'users-selector' + selectorId++;

    readonly label = input<string>();
    readonly rolesToAllowSearchingIn = input<SearchRoleGroup[]>(['tutors', 'students', 'instructors']);
    readonly multiSelect = input(true);

    readonly showUserList = input(true);

    readonly searchStudents = signal(true);
    readonly searchTutors = signal(true);
    readonly searchInstructors = signal(true);

    // icons
    faX = faX;

    readonly selectedUsers = signal<UserPublicInfoDTO[]>([]);
    readonly isSearching = signal(false);
    readonly searchFailed = signal(false);

    ngOnInit(): void {
        const rolesToAllowSearchingIn = this.rolesToAllowSearchingIn();
        if (rolesToAllowSearchingIn.includes('students')) {
            this.searchStudents.set(true);
        }
        if (rolesToAllowSearchingIn.includes('tutors')) {
            this.searchTutors.set(true);
        }
        if (rolesToAllowSearchingIn.includes('instructors')) {
            this.searchInstructors.set(true);
        }
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    usersFormatter = (user: UserPublicInfoDTO) => this.getUserLabel(user);

    trackIdentity(_index: number, item: UserPublicInfoDTO) {
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
        this.selectedUsers().splice(index, 1);
        this.onChange(this.selectedUsers());
    }

    onInputChange(event: Event): void {
        const value = (event.target as HTMLInputElement).value;
        // If the input value has fewer than 3 characters, close the suggestion popup
        if (value.length < 3) {
            this.typeAheadInstance().dismissPopup();
        }
    }

    onFilterChange() {
        this.typeAheadInstance()?.dismissPopup();
        this.searchInput().nativeElement.dispatchEvent(new Event('input'));
    }

    search: OperatorFunction<string, readonly UserPublicInfoDTO[]> = (text$: Observable<string>) =>
        text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            map((term) => (term ? term.trim().toLowerCase() : '')),
            // the three letter minimum is enforced by the server only for searching for students as they are so many
            filter((term) => term.length >= 3 || !this.searchStudents()),
            switchMap((term) => {
                const rolesToSearchIn: SearchRoleGroup[] = [];
                if (this.searchStudents()) {
                    rolesToSearchIn.push('students');
                }
                if (this.searchTutors()) {
                    rolesToSearchIn.push('tutors');
                }
                if (this.searchInstructors()) {
                    rolesToSearchIn.push('instructors');
                }
                if (rolesToSearchIn.length === 0) {
                    this.searchFailed.set(false);
                    return of([]);
                } else {
                    this.isSearching.set(true);
                    return this.courseManagementService.searchUsers(this.courseId(), term, rolesToSearchIn).pipe(
                        map((users) => users.body!),
                        map((users) => users.filter((user) => !this.selectedUsers().find((selectedUser) => selectedUser.id === user.id))),
                        tap(() => {
                            this.isSearching.set(false);
                            this.searchFailed.set(false);
                        }),
                        catchError(() => {
                            this.searchFailed.set(true);
                            this.isSearching.set(false);
                            return of([]);
                        }),
                        takeUntil(this.ngUnsubscribe),
                    );
                }
            }),
            takeUntil(this.ngUnsubscribe),
        );

    // === START CONTROL VALUE ACCESSOR ===
    onChange = (_selectedUsers: UserPublicInfoDTO[]) => {};

    // eslint-disable-next-line localRules/prefer-signal-template-state -- ControlValueAccessor callback reassigned by registerOnTouched; storing a callback does not affect rendered state, so a signal is unnecessary
    onTouched = () => {};

    registerOnChange(fn: (selectedUsers: UserPublicInfoDTO[]) => void): void {
        this.onChange = fn;
    }

    registerOnTouched(fn: () => void): void {
        this.onTouched = fn;
    }

    setDisabledState(isDisabled: boolean): void {
        this.disabled.set(isDisabled);
    }

    writeValue(selectedUsers: UserPublicInfoDTO[]): void {
        if (!selectedUsers) {
            this.selectedUsers.set([]);
            return;
        }

        if (this.multiSelect()) {
            this.selectedUsers.set(selectedUsers);
        } else {
            this.selectedUsers.set(selectedUsers.length ? [selectedUsers[0]] : []);
        }
    }
    // === END CONTROL VALUE ACCESSOR ===

    private onUserSelected(selectedUser: UserPublicInfoDTO) {
        if (selectedUser) {
            if (!this.selectedUsers().find((user) => user.id === selectedUser.id)) {
                if (this.multiSelect()) {
                    this.selectedUsers.set([...this.selectedUsers(), selectedUser]);
                } else {
                    this.selectedUsers.set([selectedUser]);
                }
                this.onChange(this.selectedUsers());
            }
        }
    }

    private resetSearchInput() {
        const searchInput = this.searchInput();
        if (searchInput) {
            searchInput.nativeElement.value = '';
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

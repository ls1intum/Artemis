import { Component, HostBinding, OnInit, ViewEncapsulation, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { Observable, Subject, catchError, map, of } from 'rxjs';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { switchMap, tap } from 'rxjs/operators';
import { UserPublicInfoDTO } from 'app/account/user/user.model';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent } from '@tumaet/ui-angular/autocomplete';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiCheckboxComponent } from '@tumaet/ui-angular/checkbox';
import { TumAetUiListComponent, TumAetUiListItemDirective } from '@tumaet/ui-angular/list';
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
 * - Uses the TUM AET UI autocomplete to provide a search input.
 * - Uses server side search to efficiently search for users.
 * - Uses a custom formatter to display the user's name and login.
 * - Search requires at least 3 characters.
 * - Always returns an array of users. In single mode the array will contain only one user.
 */
@Component({
    selector: 'jhi-course-users-selector',
    templateUrl: './course-users-selector.component.html',
    styleUrls: ['./course-users-selector.component.css'],
    providers: [
        {
            provide: NG_VALUE_ACCESSOR,
            useExisting: CourseUsersSelectorComponent,
            multi: true,
        },
    ],
    encapsulation: ViewEncapsulation.None,
    imports: [
        ProfilePictureComponent,
        TranslateDirective,
        FaIconComponent,
        ArtemisTranslatePipe,
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonDirective,
        TumAetUiCheckboxComponent,
        TumAetUiListComponent,
        TumAetUiListItemDirective,
    ],
})
export class CourseUsersSelectorComponent implements ControlValueAccessor, OnInit {
    private courseManagementService = inject(CourseManagementService);

    @HostBinding('class.course-users-selector') hostClass = true;

    readonly disabled = signal(false);
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
    /** The users found by the last search, offered in the suggestions of the search input. */
    readonly suggestions = signal<UserPublicInfoDTO[]>([]);
    readonly isSearching = signal(false);
    readonly searchFailed = signal(false);

    /** What the search input shows, to repeat the search when a role filter changes. */
    private readonly searchText = signal('');
    private readonly searchTerms = new Subject<string>();

    constructor() {
        // The latest search wins: switching to a new term drops the response of the previous one.
        this.searchTerms
            .pipe(
                switchMap((term) => this.findUsers(term)),
                takeUntilDestroyed(),
            )
            .subscribe((users) => this.suggestions.set(users));
    }

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

    onSelectItem(event: TumAetUiAutoCompleteOptionEvent) {
        this.onUserSelected(event.value as UserPublicInfoDTO);
        this.searchText.set('');
    }

    onDelete(index: number) {
        this.selectedUsers().splice(index, 1);
        this.onChange(this.selectedUsers());
    }

    onInputChange(event: Event): void {
        this.searchText.set((event.target as HTMLInputElement).value);
    }

    /** Offers the users of the changed roles for the text that is already typed. */
    onFilterChange() {
        const term = this.searchText().trim();
        if (term) {
            this.searchTerms.next(term.toLowerCase());
        } else {
            // Without text the search input only suggests users once it is focused again.
            this.suggestions.set([]);
        }
    }

    requestSearch(query: string) {
        this.searchTerms.next(query.trim().toLowerCase());
    }

    private findUsers(term: string): Observable<UserPublicInfoDTO[]> {
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
        // the three letter minimum is enforced by the server only for searching for students as they are so many
        if (rolesToSearchIn.length === 0 || (term.length < 3 && this.searchStudents())) {
            this.isSearching.set(false);
            this.searchFailed.set(false);
            return of([]);
        }
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
        );
    }

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

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

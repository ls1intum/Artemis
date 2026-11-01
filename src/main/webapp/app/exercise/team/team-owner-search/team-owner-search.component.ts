import { Component, DestroyRef, OnInit, inject, input, output, signal, viewChild } from '@angular/core';
import { Observable, Subject, combineLatest, of } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { Course, CourseRoleSlug } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent } from '@tumaet/ui-angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-team-owner-search',
    templateUrl: './team-owner-search.component.html',
    imports: [FormsModule, TumAetUiAutoCompleteComponent, ArtemisTranslatePipe],
})
export class TeamOwnerSearchComponent implements OnInit {
    private courseService = inject(CourseManagementService);
    private readonly destroyRef = inject(DestroyRef);

    readonly autocomplete = viewChild<TumAetUiAutoCompleteComponent>('autocomplete');

    readonly inputDisabled = input(false);

    readonly course = input.required<Course>();
    readonly exercise = input.required<Exercise>();
    readonly team = input.required<Team>();

    readonly selectOwner = output<User>();
    readonly searching = output<boolean>();
    readonly searchQueryTooShort = output<boolean>();
    readonly searchFailed = output<boolean>();
    readonly searchNoResults = output<string | undefined>();

    owner?: User;
    ownerOptions: User[] = [];
    ownerOptionsLoaded = false;

    private readonly displayValue = signal('');
    get inputDisplayValue(): string {
        return this.displayValue();
    }
    set inputDisplayValue(value: string) {
        this.displayValue.set(value);
    }

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        const team = this.team();
        if (team.owner) {
            this.owner = deepClone(team.owner);
            this.inputDisplayValue = this.searchResultFormatter(this.owner);
        }
    }

    readonly suggestions = signal<{ label: string; value: User; disabled: boolean }[]>([]);
    private readonly searchQuery = new Subject<string>();

    constructor() {
        this.onSearch(this.searchQuery)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((values) => {
                this.suggestions.set(values.map((value) => ({ label: this.searchResultFormatter(value), value, disabled: false })));
            });
    }

    requestSearch(query: string): void {
        this.searchQuery.next(query);
    }

    selectOption(event: TumAetUiAutoCompleteOptionEvent): void {
        const option = this.suggestions().find((suggestion) => suggestion === event.value);
        if (option && !option.disabled) {
            this.onAutocompleteSelect(option.value);
        }
    }

    onAutocompleteSelect = (owner: User) => {
        this.inputDisplayValue = this.searchResultFormatter(owner);
        this.selectOwner.emit(owner);
    };

    searchInputFormatter = () => {
        return this.inputDisplayValue;
    };

    searchResultFormatter = (owner: User): string => {
        const { login, name } = owner;
        return `${name} (${login})`;
    };

    /**
     * Check if a given login/name is included in a given user
     * @param {string} loginOrName - login/name to search for
     * @param {User} user - User to search through
     */
    searchMatchesUser(loginOrName: string, user: User) {
        return [user.login, user.name].some((field) => {
            return (field || '').toLowerCase().includes(loginOrName.toLowerCase());
        });
    }

    onSearch = (text$: Observable<string>) => {
        // Mirror the debounce/distinctUntilChanged pattern used in the sibling
        // team-student-search component: under heavy typing each keystroke would otherwise
        // fire an HTTP via switchMap and cancel the previous one, so the final response
        // can take 10-30s under load and the listbox never appears. Coalesce rapid typing
        // and drop duplicates before the request fires.
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            tap(() => {
                this.searchNoResults.emit(undefined);
            }),
            switchMap((loginOrName) => {
                this.searchFailed.emit(false);
                this.searching.emit(true);
                // If owner options have already been loaded once, do not load them again and reuse the already loaded ones
                const ownerOptionsSource$ = this.ownerOptionsLoaded ? of(this.ownerOptions) : this.loadOwnerOptions();
                return combineLatest([of(loginOrName), ownerOptionsSource$]);
            }),
            tap(() => this.searching.emit(false)),
            switchMap(([loginOrName, ownerOptions]) => {
                // Filter list of all owner options by the search term
                const match = (user: User) => this.searchMatchesUser(loginOrName, user);
                return combineLatest([of(loginOrName), of(!ownerOptions ? ownerOptions : ownerOptions.filter(match))]);
            }),
            tap(([loginOrName, ownerOptions]) => {
                if (ownerOptions && ownerOptions.length === 0) {
                    this.searchNoResults.emit(loginOrName);
                }
            }),
            map(([, ownerOptions]) => ownerOptions || []),
        );
    };

    /**
     * Load options of team owner
     */
    loadOwnerOptions() {
        return this.courseService.getAllUsersInCourseRole(this.course().id!, CourseRoleSlug.TUTORS).pipe(
            map((usersResponse) => usersResponse.body!),
            tap((ownerOptions) => {
                this.ownerOptions = ownerOptions;
                this.ownerOptionsLoaded = true;
            }),
            catchError(() => {
                this.ownerOptionsLoaded = false;
                this.searchFailed.emit(true);
                return of(undefined);
            }),
        );
    }
}

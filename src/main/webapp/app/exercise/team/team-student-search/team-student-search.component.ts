import { hydrate } from 'app/foundation/util/deep-clone.util';
import { FormsModule } from '@angular/forms';
import { Component, DestroyRef, inject, input, output, signal, viewChild } from '@angular/core';
import { Observable, Subject, combineLatest, of } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamSearchUser } from 'app/exercise/shared/entities/team/team-search-user.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TumAetUiAutoCompleteComponent, TumAetUiAutoCompleteOptionEvent } from '@tumaet/ui-angular';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-team-student-search',
    templateUrl: './team-student-search.component.html',
    imports: [FormsModule, TumAetUiAutoCompleteComponent, ArtemisTranslatePipe],
})
export class TeamStudentSearchComponent {
    private teamService = inject(TeamService);
    private readonly destroyRef = inject(DestroyRef);

    readonly autocomplete = viewChild<TumAetUiAutoCompleteComponent>('autocomplete');

    readonly course = input.required<Course>();
    readonly exercise = input.required<Exercise>();
    readonly team = input.required<Team>();
    readonly studentsFromPendingTeam = input<User[]>([]);

    readonly selectStudent = output<User>();
    readonly searching = output<boolean>();
    readonly searchQueryTooShort = output<boolean>();
    readonly searchFailed = output<boolean>();
    readonly searchNoResults = output<string | undefined>();

    private readonly displayValue = signal('');
    get inputDisplayValue(): string {
        return this.displayValue();
    }
    set inputDisplayValue(value: string) {
        this.displayValue.set(value);
    }

    readonly suggestions = signal<{ label: string; value: TeamSearchUser; disabled: boolean }[]>([]);
    private readonly searchQuery = new Subject<string>();

    constructor() {
        this.onSearch(this.searchQuery)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((values) => {
                this.suggestions.set(values.map((value) => ({ label: this.searchResultFormatter(value), value, disabled: !this.userCanBeAddedToPendingTeam(value) })));
            });
    }

    requestSearch(query: string): void {
        this.searchQuery.next(query);
    }

    selectOption(event: TumAetUiAutoCompleteOptionEvent): void {
        const option = this.suggestions().find((suggestion) => suggestion === event.value);
        if (option && !option.disabled) {
            this.onAutocompleteSelect(hydrate(new User(), option.value));
        }
    }

    onAutocompleteSelect = (student: User) => {
        this.inputDisplayValue = '';
        this.autocomplete()?.clear();
        this.selectStudent.emit(student);
    };

    searchInputFormatter = () => {
        return this.inputDisplayValue;
    };

    searchResultFormatter = (student: Pick<User, 'name' | 'login'>) => {
        const { login, name } = student;
        return `${name} (${login})`;
    };

    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            tap(() => {
                this.searchQueryTooShort.emit(false);
                this.searchFailed.emit(false);
                this.searchNoResults.emit(undefined);
            }),
            tap(() => this.searching.emit(true)),
            switchMap((loginOrName) => {
                if (loginOrName.length < 3) {
                    this.searchQueryTooShort.emit(true);
                    return combineLatest([of(loginOrName), of(undefined)]);
                }
                return combineLatest([
                    of(loginOrName),
                    this.teamService
                        .searchInCourseForExerciseTeam(this.course(), this.exercise(), loginOrName)
                        .pipe(map((usersResponse) => usersResponse.body!))
                        .pipe(
                            catchError(() => {
                                this.searchFailed.emit(true);
                                return of(undefined);
                            }),
                        ),
                ]);
            }),
            tap(() => this.searching.emit(false)),
            tap(([loginOrName, users]) => {
                // "Query too short" (no request performed) or "Search request failed" => {users} will be undefined
                // "Successful search request" => {users} will be an array (length 0 if no students were found)
                if (users && users.length === 0) {
                    this.searchNoResults.emit(loginOrName);
                }
            }),
            map(([, users]) => users || []),
        );
    };

    private userCanBeAddedToPendingTeam(user: TeamSearchUser): boolean {
        const team = this.team();
        if (
            this.studentsFromPendingTeam()
                .map((s) => s.id)
                .includes(user.id)
        ) {
            // If a student is already part of the pending team, they cannot (!) be added again
            return false;
        } else if (!user.assignedTeamId) {
            // If a student is not yet assigned to any team, they can be added
            return true;
        } else if (!team.id) {
            // If a student is assigned to an existing team but this team is just being created, they cannot (!) be added
            return false;
        }
        // If a student is assigned to a team, they can only be added if they are assigned to this team itself
        // This can happen if they were removed from the pending team and are then added back again
        return user.assignedTeamId === team.id;
    }
}

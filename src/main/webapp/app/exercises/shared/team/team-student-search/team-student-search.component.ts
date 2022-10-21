import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Observable, combineLatest, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { get } from 'lodash-es';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamSearchUser } from 'app/entities/team-search-user.model';
import { Team } from 'app/entities/team.model';

@Component({
    selector: 'jhi-team-student-search',
    templateUrl: './team-student-search.component.html',
})
export class TeamStudentSearchComponent {
    @ViewChild('ngbTypeahead', { static: false }) ngbTypeahead: ElementRef;

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() team: Team;
    @Input() studentsFromPendingTeam: User[] = [];

    @Output() selectStudent = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchQueryTooShort = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();
    @Output() searchNoResults = new EventEmitter<string | undefined>();

    inputDisplayValue: string;

    constructor(private teamService: TeamService) {}

    onAutocompleteSelect = (student: User) => {
        this.inputDisplayValue = '';
        this.selectStudent.emit(student);
    };

    searchInputFormatter = () => {
        return this.inputDisplayValue;
    };

    searchResultFormatter = (student: User) => {
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
                        .searchInCourseForExerciseTeam(this.course, this.exercise, loginOrName)
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
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.typeaheadButtons.length; i++) {
                        if (!this.userCanBeAddedToPendingTeam(users[i])) {
                            this.typeaheadButtons[i].setAttribute('disabled', '');
                        }
                    }
                });
            }),
        );
    };

    private userCanBeAddedToPendingTeam(user: TeamSearchUser): boolean {
        if (this.studentsFromPendingTeam.map((s) => s.id).includes(user.id)) {
            // If a student is already part of the pending team, they cannot (!) be added again
            return false;
        } else if (!user.assignedTeamId) {
            // If a student is not yet assigned to any team, they can be added
            return true;
        } else if (!this.team.id) {
            // If a student is assigned to an existing team but this team is just being created, they cannot (!) be added
            return false;
        }
        // If a student is assigned to a team, they can only be added if they are assigned to this team itself
        // This can happen if they were removed from the pending team and are then added back again
        return user.assignedTeamId === this.team.id;
    }

    private get typeaheadButtons() {
        return get(this.ngbTypeahead, 'nativeElement.nextSibling.children', []);
    }
}

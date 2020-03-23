import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { get } from 'lodash';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamSearchUser } from 'app/entities/team-search-student.model';

@Component({
    selector: 'jhi-team-student-search',
    templateUrl: './team-student-search.component.html',
})
export class TeamStudentSearchComponent {
    @ViewChild('ngbTypeahead', { static: false }) ngbTypeahead: ElementRef;

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() studentsFromTeam: User[] = [];
    @Input() studentsFromPendingTeam: User[] = [];

    @Output() selectStudent = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();

    users: User[] = [];

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
            tap(() => this.searchFailed.emit(false)),
            tap(() => this.searching.emit(true)),
            switchMap((loginOrName) => {
                if (loginOrName.length < 3) {
                    return of([]);
                }
                return this.teamService
                    .searchInCourseForExerciseTeam(this.course, this.exercise, loginOrName)
                    .pipe(map((usersResponse) => usersResponse.body!))
                    .pipe(
                        catchError(() => {
                            this.searchFailed.emit(true);
                            return of([]);
                        }),
                    );
            }),
            tap((users) => {
                setTimeout(() => {
                    for (let i = 0; i < this.typeaheadButtons.length; i++) {
                        if (!this.userCanBeAddedToPendingTeam(users[i])) {
                            this.typeaheadButtons[i].setAttribute('disabled', '');
                        }
                    }
                });
            }),
            tap(() => this.searching.emit(false)),
        );
    };

    private userCanBeAddedToPendingTeam(user: TeamSearchUser): boolean {
        if (this.studentsFromPendingTeam.map((s) => s.id).includes(user.id)) {
            // If a student is already part of the pending team, they cannot be added again
            return false;
        } else if (this.studentsFromTeam.map((s) => s.id).includes(user.id)) {
            // If a student is not part of the pending team but was in the original team, they can be added back
            return true;
        }
        // If a student was neither in the original team nor is in the pending team,
        // they can be added if they are not assigned to another team yet
        return !user.assignedToTeam;
    }

    private get typeaheadButtons() {
        return get(this.ngbTypeahead, 'nativeElement.nextSibling.children', []);
    }
}

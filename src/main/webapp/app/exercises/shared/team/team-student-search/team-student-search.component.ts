import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { get } from 'lodash';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { TeamService } from 'app/exercises/shared/team/team.service';

@Component({
    selector: 'jhi-team-student-search',
    templateUrl: './team-student-search.component.html',
})
export class TeamStudentSearchComponent {
    @ViewChild('ngbTypeahead', { static: false }) ngbTypeahead: ElementRef;

    @Input() course: Course;
    @Input() exercise: Exercise;

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
        return `${login} (${name})`;
    };

    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            tap(() => this.searchFailed.emit(false)),
            tap(() => this.searching.emit(true)),
            switchMap(loginOrName => {
                if (loginOrName.length < 3) {
                    return of([]);
                }
                return this.teamService
                    .searchInCourseForExerciseTeam(this.course, this.exercise, loginOrName)
                    .pipe(map(usersResponse => usersResponse.body!))
                    .pipe(
                        catchError(() => {
                            this.searchFailed.emit(true);
                            return of([]);
                        }),
                    );
            }),
            tap(users => {
                setTimeout(() => {
                    for (let i = 0; i < this.typeaheadButtons.length; i++) {
                        if (users[i].assignedToTeam) {
                            this.typeaheadButtons[i].setAttribute('disabled', '');
                        }
                    }
                });
            }),
            tap(() => this.searching.emit(false)),
        );
    };

    private get typeaheadButtons() {
        return get(this.ngbTypeahead, 'nativeElement.nextSibling.children', []);
    }
}

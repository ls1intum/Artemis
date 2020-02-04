import { Component, Input, Output, EventEmitter } from '@angular/core';
import { Course } from 'app/entities/course';
import { Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { debounceTime, distinctUntilChanged, map, switchMap, tap, catchError } from 'rxjs/operators';
import { UserService } from 'app/core/user/user.service';

@Component({
    selector: 'jhi-student-search',
    templateUrl: './student-search.component.html',
})
export class StudentSearchComponent {
    @Input() course: Course;

    @Output() selectStudent = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();

    users: User[] = [];

    inputDisplayValue: string;

    constructor(private userService: UserService) {}

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
                return this.userService
                    .searchInCourse(this.course, loginOrName)
                    .pipe(map(usersResponse => usersResponse.body!))
                    .pipe(
                        catchError(() => {
                            this.searchFailed.emit(true);
                            return of([]);
                        }),
                    );
            }),
            tap(() => this.searching.emit(false)),
        );
    };
}

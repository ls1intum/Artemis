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

    users: User[] = [];
    searching = false;
    searchFailed = false;

    constructor(private userService: UserService) {}

    onAutocompleteSelect = (student: User) => {
        console.log('selected student', student);
        this.selectStudent.emit(student);
    };

    searchResultFormatter = (student: User) => {
        const { login, name } = student;
        return `${login} (${name})`;
    };

    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            tap(() => (this.searching = true)),
            switchMap(login => {
                if (login.length < 3) {
                    return of([]);
                }
                return this.userService
                    .searchInCourse(this.course, login)
                    .pipe(map(usersResponse => usersResponse.body!))
                    .pipe(
                        tap(() => (this.searchFailed = false)),
                        catchError(() => {
                            this.searchFailed = true;
                            return of([]);
                        }),
                    );
            }),
            tap(() => (this.searching = false)),
        );
    };
}

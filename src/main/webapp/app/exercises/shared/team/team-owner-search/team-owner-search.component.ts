import { Component, ElementRef, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { combineLatest, Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from 'rxjs/operators';
import { Course, CourseGroup } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Team } from 'app/entities/team.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';

@Component({
    selector: 'jhi-team-owner-search',
    templateUrl: './team-owner-search.component.html',
})
export class TeamOwnerSearchComponent implements OnInit {
    @ViewChild('ngbTypeahead', { static: false }) ngbTypeahead: ElementRef;

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() team: Team;

    @Output() selectOwner = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchQueryTooShort = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();
    @Output() searchNoResults = new EventEmitter<string | null>();

    users: User[] = [];

    inputDisplayValue: string;

    constructor(private courseService: CourseManagementService) {}

    ngOnInit() {
        if (this.team.owner) {
            this.inputDisplayValue = this.searchResultFormatter(this.team.owner);
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

    onSearch = (text$: Observable<string>) => {
        return text$.pipe(
            debounceTime(200),
            distinctUntilChanged(),
            tap(() => {
                this.searchQueryTooShort.emit(false);
                this.searchFailed.emit(false);
                this.searchNoResults.emit(null);
            }),
            tap(() => this.searching.emit(true)),
            switchMap((loginOrName) => {
                if (loginOrName.length < 3) {
                    this.searchQueryTooShort.emit(true);
                    return combineLatest([of(loginOrName), of(null)]);
                }
                return combineLatest([
                    of(loginOrName),
                    this.courseService
                        .getAllUsersInCourseGroup(this.course.id, CourseGroup.TUTORS)
                        .pipe(map((usersResponse) => usersResponse.body!))
                        .pipe(
                            catchError(() => {
                                this.searchFailed.emit(true);
                                return of(null);
                            }),
                        ),
                ]);
            }),
            tap(() => this.searching.emit(false)),
            tap(([loginOrName, users]) => {
                // "Query too short" (no request performed) or "Search request failed" => {users} will be null
                // "Successful search request" => {users} will be an array (length 0 if no owners were found)
                if (users && users.length === 0) {
                    this.searchNoResults.emit(loginOrName);
                }
            }),
            map(([_, users]) => users || []),
        );
    };
}

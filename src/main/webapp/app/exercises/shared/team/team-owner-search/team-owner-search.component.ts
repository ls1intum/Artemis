import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { combineLatest, Observable, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { filter, map, switchMap, tap } from 'rxjs/operators';
import { Course, CourseGroup } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Team } from 'app/entities/team.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { cloneDeep } from 'lodash';
import { Subject, merge } from 'rxjs';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-team-owner-search',
    templateUrl: './team-owner-search.component.html',
})
export class TeamOwnerSearchComponent implements OnInit {
    @ViewChild('instance', { static: true }) ngbTypeahead: NgbTypeahead;
    focus$ = new Subject<string>();
    click$ = new Subject<string>();

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() team: Team;

    @Output() selectOwner = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchQueryTooShort = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();
    @Output() searchNoResults = new EventEmitter<string | null>();

    owner: User;
    ownerOptions: User[];

    inputDisplayValue: string;

    constructor(private courseService: CourseManagementService) {}

    ngOnInit() {
        if (this.team.owner) {
            this.owner = cloneDeep(this.team.owner);
            this.inputDisplayValue = this.searchResultFormatter(this.owner);
        }

        setTimeout(() => {
            this.searchFailed.emit(false);
            this.searching.emit(true);
            this.courseService.getAllUsersInCourseGroup(this.course.id, CourseGroup.TUTORS).subscribe(
                (usersResponse) => {
                    this.ownerOptions = usersResponse.body!;
                    this.searching.emit(false);
                },
                (error) => {
                    this.searchFailed.emit(true);
                    this.searching.emit(false);
                },
            );
        });
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
        const clicksWithClosedPopup$ = this.click$.pipe(filter(() => !this.ngbTypeahead.isPopupOpen()));
        const inputFocus$ = this.focus$;

        return merge(text$, inputFocus$, clicksWithClosedPopup$).pipe(
            tap(() => {
                this.searchNoResults.emit(null);
            }),
            switchMap((loginOrName) => {
                const match = (user: User) => [user.login, user.name].some((field) => (field || '').includes(loginOrName));
                return combineLatest([of(loginOrName), of(this.ownerOptions.filter(match))]);
            }),
            tap(([loginOrName, users]) => {
                if (users && users.length === 0) {
                    this.searchNoResults.emit(loginOrName);
                }
            }),
            map(([_, users]) => users || []),
        );
    };
}

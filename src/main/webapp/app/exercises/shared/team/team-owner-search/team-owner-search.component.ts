import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from '@angular/core';
import { Observable, Subject, combineLatest, merge, of } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { Course, CourseGroup } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Team } from 'app/entities/team.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { cloneDeep } from 'lodash-es';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-team-owner-search',
    templateUrl: './team-owner-search.component.html',
})
export class TeamOwnerSearchComponent implements OnInit {
    @ViewChild('instance', { static: true }) ngbTypeahead: NgbTypeahead;
    focus = new Subject<string>();
    click = new Subject<string>();

    @Input() inputDisabled: boolean;

    @Input() course: Course;
    @Input() exercise: Exercise;
    @Input() team: Team;

    @Output() selectOwner = new EventEmitter<User>();
    @Output() searching = new EventEmitter<boolean>();
    @Output() searchQueryTooShort = new EventEmitter<boolean>();
    @Output() searchFailed = new EventEmitter<boolean>();
    @Output() searchNoResults = new EventEmitter<string | undefined>();

    owner: User;
    ownerOptions: User[] = [];
    ownerOptionsLoaded = false;

    inputDisplayValue: string;

    constructor(private courseService: CourseManagementService) {}

    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        if (this.team.owner) {
            this.owner = cloneDeep(this.team.owner);
            this.inputDisplayValue = this.searchResultFormatter(this.owner);
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
        const clicksWithClosedPopup$ = this.click.pipe(filter(() => !this.ngbTypeahead.isPopupOpen()));
        const inputFocus$ = this.focus;

        return merge(text$, inputFocus$, clicksWithClosedPopup$).pipe(
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
        return this.courseService.getAllUsersInCourseGroup(this.course.id!, CourseGroup.TUTORS).pipe(
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

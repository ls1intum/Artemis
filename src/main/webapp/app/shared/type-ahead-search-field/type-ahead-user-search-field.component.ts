import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Observable, OperatorFunction, catchError, of, switchMap, tap } from 'rxjs';
import { UserService } from 'app/core/user/user.service';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-type-ahead-user-search-field',
    templateUrl: './type-ahead-user-search-field.component.html',
    styleUrls: ['./type-ahead-user-search-field.component.scss'],
})
export class TypeAheadUserSearchFieldComponent {
    @Input() loginOrName: string;
    @Output() loginOrNameChange = new EventEmitter<string>();

    searching = false;
    searchFailed = false;
    searchNoResults = false;
    searchQueryTooShort = true;

    readonly faCircleNotch = faCircleNotch;
    readonly minSearchQueryLength = 3;

    constructor(private userService: UserService) {}

    search: OperatorFunction<string, readonly User[]> = (login: Observable<string>) => {
        console.log('search');
        this.searching = true;
        this.searchFailed = false;
        const observable = login.pipe(
            tap((loginOrName) => console.log('searching for ' + loginOrName)),
            switchMap((loginOrName: string) => {
                console.log('searching for ' + loginOrName);
                if (loginOrName.length < 3) {
                    return of([]);
                }
                return this.userService.search(loginOrName).pipe(
                    switchMap((usersResponse) => of(usersResponse.body!)),
                    tap((users) => {
                        console.log('found ' + users.length + ' users');
                        this.searching = false;
                        this.searchNoResults = users.length === 0;
                    }),
                    catchError(() => {
                        console.log('error');
                        this.searching = false;
                        this.searchFailed = true;
                        return of([]);
                    }),
                );
            }),
        );
        return observable;
    };

    onChange() {
        this.loginOrNameChange.emit(this.loginOrName);
        this.searchQueryTooShort = this.loginOrName.length < this.minSearchQueryLength - 1;
    }
    userFormatter = (result: User) => result.login! + ' ' + result.name!;
}

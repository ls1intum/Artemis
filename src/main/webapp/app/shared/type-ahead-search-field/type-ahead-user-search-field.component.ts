import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
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
    private userService = inject(UserService);

    @Input() loginOrName: string;
    @Output() loginOrNameChange = new EventEmitter<string>();

    searching = false;
    searchFailed = false;
    searchNoResults = false;
    searchQueryTooShort = true;

    readonly faCircleNotch = faCircleNotch;
    readonly MIN_SEARCH_QUERY_LENGTH = 3;

    search: OperatorFunction<string, readonly User[]> = (login: Observable<string>) => {
        this.searchFailed = false;
        return login.pipe(
            switchMap((loginOrName: string) => {
                if (loginOrName.length < this.MIN_SEARCH_QUERY_LENGTH) {
                    this.searchQueryTooShort = true;
                    this.searching = false;
                    return of([]);
                } else {
                    this.searchQueryTooShort = false;
                }
                this.searching = true;

                return this.userService.search(loginOrName).pipe(
                    switchMap((usersResponse) => of(usersResponse.body!)),
                    tap((users) => {
                        this.searching = false;
                        this.searchNoResults = users.length === 0;
                    }),
                    catchError(() => {
                        this.searching = false;
                        this.searchFailed = true;
                        return of([]);
                    }),
                );
            }),
        );
    };

    onChange() {
        const user = this.loginOrName as unknown as User;
        // this is a user object returned by search, but we are only interested in the login
        // if we don't do this, the user object will be converted to a string and passed to the parent component
        // before we sent a request to the server this is a string, and we can emit it directly
        if (user && user.login) {
            this.loginOrNameChange.emit(user.login);
        } else {
            this.loginOrNameChange.emit(this.loginOrName);
        }
        this.searchQueryTooShort = this.loginOrName.length < this.MIN_SEARCH_QUERY_LENGTH;
    }

    resultFormatter = (result: User) => result.name! + ' (' + result.login! + ')';
    inputFormatter(input: User | string) {
        // here applies the same as in onChange()
        const user = input as unknown as User;
        if (user && user.login) {
            return user.login!;
        } else {
            return input as string;
        }
    }
}

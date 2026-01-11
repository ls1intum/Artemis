import { Component, inject, model, signal } from '@angular/core';
import { Observable, OperatorFunction, catchError, of, switchMap, tap } from 'rxjs';
import { UserService } from 'app/core/user/shared/user.service';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/core/user/user.model';
import { FormsModule } from '@angular/forms';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-type-ahead-user-search-field',
    templateUrl: './type-ahead-user-search-field.component.html',
    styleUrls: ['./type-ahead-user-search-field.component.scss'],
    imports: [TranslateDirective, FormsModule, NgbTypeahead, FaIconComponent, ArtemisTranslatePipe],
})
export class TypeAheadUserSearchFieldComponent {
    private readonly userService = inject(UserService);

    readonly loginOrName = model<string>('');

    readonly searching = signal(false);
    readonly searchFailed = signal(false);
    readonly searchNoResults = signal(false);
    readonly searchQueryTooShort = signal(true);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly MIN_SEARCH_QUERY_LENGTH = 3;

    search: OperatorFunction<string, readonly User[]> = (login: Observable<string>) => {
        this.searchFailed.set(false);
        return login.pipe(
            switchMap((loginOrName: string) => {
                if (loginOrName.length < this.MIN_SEARCH_QUERY_LENGTH) {
                    this.searchQueryTooShort.set(true);
                    this.searching.set(false);
                    return of([]);
                } else {
                    this.searchQueryTooShort.set(false);
                }
                this.searching.set(true);

                return this.userService.search(loginOrName).pipe(
                    switchMap((usersResponse) => of(usersResponse.body!)),
                    tap((users) => {
                        this.searching.set(false);
                        this.searchNoResults.set(users.length === 0);
                    }),
                    catchError(() => {
                        this.searching.set(false);
                        this.searchFailed.set(true);
                        return of([]);
                    }),
                );
            }),
        );
    };

    onChange(): void {
        const currentValue = this.loginOrName();
        const user = currentValue as unknown as User;
        // this is a user object returned by search, but we are only interested in the login
        // if we don't do this, the user object will be converted to a string and passed to the parent component
        // before we sent a request to the server this is a string, and we can emit it directly
        if (user && user.login) {
            this.loginOrName.set(user.login);
        }
        this.searchQueryTooShort.set(this.loginOrName().length < this.MIN_SEARCH_QUERY_LENGTH);
    }

    resultFormatter = (result: User): string => result.name! + ' (' + result.login! + ')';

    inputFormatter(input: User | string): string {
        // here applies the same as in onChange()
        const user = input as unknown as User;
        if (user && user.login) {
            return user.login!;
        } else {
            return input as string;
        }
    }
}

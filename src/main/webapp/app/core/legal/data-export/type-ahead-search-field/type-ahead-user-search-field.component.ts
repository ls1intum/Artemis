import { Component, inject, model, signal } from '@angular/core';
import { Observable, OperatorFunction, catchError, map, of, switchMap, tap } from 'rxjs';
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

    readonly loginOrName = model<string | User>('');

    readonly searching = signal(false);
    readonly searchFailed = signal(false);
    readonly searchNoResults = signal(false);
    readonly searchQueryTooShort = signal(true);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly MIN_SEARCH_QUERY_LENGTH = 3;

    search: OperatorFunction<string, readonly User[]> = (login: Observable<string>) => {
        // Reset all status flags at the beginning before any branching
        this.searchFailed.set(false);
        this.searchNoResults.set(false);
        this.searchQueryTooShort.set(false);
        return login.pipe(
            switchMap((loginOrName: string) => {
                if (loginOrName.length < this.MIN_SEARCH_QUERY_LENGTH) {
                    this.searchQueryTooShort.set(true);
                    this.searching.set(false);
                    return of([]);
                }
                this.searching.set(true);

                return this.userService.search(loginOrName).pipe(
                    map((usersResponse) => usersResponse.body ?? []),
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
        // When user selects from typeahead, currentValue is a User object
        // We extract the login string for the parent component
        const user = typeof currentValue === 'string' ? undefined : currentValue;
        if (user?.login) {
            this.loginOrName.set(user.login);
        }
        const value = this.loginOrName();
        this.searchQueryTooShort.set(typeof value === 'string' && value.length < this.MIN_SEARCH_QUERY_LENGTH);
    }

    resultFormatter = (result: User): string => result.name! + ' (' + result.login! + ')';

    inputFormatter(input: User | string): string {
        return typeof input === 'string' ? input : (input.login ?? '');
    }
}

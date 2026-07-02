import { Component, inject, model, signal } from '@angular/core';
import { Observable, OperatorFunction, Subject, catchError, map, of, switchMap, tap } from 'rxjs';
import { UserService } from 'app/account/user/shared/user.service';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { User } from 'app/account/user/user.model';
import { FormsModule } from '@angular/forms';
import { AutoCompleteCompleteEvent, AutoCompleteModule, AutoCompleteSelectEvent } from 'primeng/autocomplete';
import { TagModule } from 'primeng/tag';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-type-ahead-user-search-field',
    templateUrl: './type-ahead-user-search-field.component.html',
    styleUrls: ['./type-ahead-user-search-field.component.scss'],
    imports: [TranslateDirective, FormsModule, AutoCompleteModule, TagModule, FaIconComponent, ArtemisTranslatePipe],
})
export class TypeAheadUserSearchFieldComponent {
    private readonly userService = inject(UserService);

    readonly loginOrName = model<string | User>('');

    readonly searching = signal(false);
    readonly searchFailed = signal(false);
    readonly searchNoResults = signal(false);
    readonly searchQueryTooShort = signal(true);

    readonly suggestions = signal<User[]>([]);

    protected readonly faCircleNotch = faCircleNotch;
    protected readonly MIN_SEARCH_QUERY_LENGTH = 3;

    /** Drives the search pipeline from the PrimeNG (completeMethod) event. */
    private readonly query$ = new Subject<string>();

    constructor() {
        this.search(this.query$).subscribe((users) => this.suggestions.set(users));
    }

    search: OperatorFunction<string, User[]> = (login: Observable<string>) => {
        return login.pipe(
            tap(() => {
                // Reset all status flags at the beginning before any branching
                this.searchFailed.set(false);
                this.searchNoResults.set(false);
                this.searchQueryTooShort.set(false);
            }),
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

    /** Called by p-autoComplete on each keystroke; forwards the query into the search pipeline. */
    onComplete(event: AutoCompleteCompleteEvent): void {
        this.query$.next(event.query);
    }

    /** Called by p-autoComplete when a user is picked from the dropdown. */
    onSelectUser(event: AutoCompleteSelectEvent): void {
        this.loginOrName.set(event.value as User);
        this.onChange();
    }

    onChange(): void {
        const currentValue = this.loginOrName();
        // When user selects from typeahead, currentValue is a User object
        // We extract the login string for the parent component
        const user = typeof currentValue === 'string' ? undefined : currentValue;
        // Compute normalized string value for validation (handles User without login)
        const normalizedValue = typeof currentValue === 'string' ? currentValue : (currentValue.login ?? '');

        if (user?.login) {
            this.loginOrName.set(user.login);
        }
        this.searchQueryTooShort.set(normalizedValue.length < this.MIN_SEARCH_QUERY_LENGTH);
    }

    resultFormatter = (result: User): string => (result.name ?? '<no name>') + ' (' + (result.login ?? '<no login>') + ')';

    inputFormatter(input: User | string): string {
        return typeof input === 'string' ? input : (input.login ?? '');
    }
}

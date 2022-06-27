import { Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';

export const USER_COUNT_LIMIT = 10;
export const PLACEHOLDER_USER_REACTED = 'REPLACE_WITH_TRANSLATED_YOU';

/**
 * Pipe to show list of reacting users when hovering over emojis.
 */
@Pipe({
    name: 'reactingUsersOnPosting',
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    constructor(private translateService: TranslateService) {}

    /**
     * Transforms a given name list of reacting users to a prosaic, translated string
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {Observable<string>} observable of concatenated, translated (and shortened if required) string of reacting users
     */
    transform(reactingUsers: string[]): Observable<string> {
        return new Observable((observer: any) => {
            observer.next(this.updateReactingUsersString(reactingUsers));
            this.translateService.onLangChange.subscribe(() => {
                observer.next(this.updateReactingUsersString(reactingUsers));
            });
        });
    }

    /**
     * Manipulates the `reactingUsersString` variable taking into account the language, if the string has to be stripped,
     * and if the currently logged-in user is addressed directly ('you' instead of name)
     * @param {string[]} reactingUsers
     * @private
     */
    updateReactingUsersString(reactingUsers: string[]): string {
        // determine if the list includes the currently logged-in user
        if (reactingUsers.includes(PLACEHOLDER_USER_REACTED)) {
            if (reactingUsers.length === 1) {
                // set "you" as ready-to-use reacting users string
                return this.translateService.instant('artemisApp.metis.you');
            }
            // if more than the currently logged-in user reacted,
            // remove placeholder and replace it with directly addressing currently logged-in user
            reactingUsers = reactingUsers.filter((user) => user !== PLACEHOLDER_USER_REACTED);
            reactingUsers = [this.translateService.instant('artemisApp.metis.you')].concat(reactingUsers);
        }
        // determine if list has to be trimmed
        const numberOfReactingUsers = reactingUsers.length;
        if (numberOfReactingUsers > USER_COUNT_LIMIT) {
            // prepare trimmed list
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT);
            return reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltipTrimmed', { number: numberOfReactingUsers - USER_COUNT_LIMIT });
        } else {
            // prepare list
            let listOfReactingUsers = reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltip');
            // replace last comma by "and"
            const lastCommaIndex = listOfReactingUsers.lastIndexOf(',');
            if (lastCommaIndex > -1) {
                const beforeLastComma = listOfReactingUsers.substring(0, lastCommaIndex);
                const afterLastComma = listOfReactingUsers.substring(lastCommaIndex + 2, listOfReactingUsers.length);
                listOfReactingUsers = beforeLastComma + this.translateService.instant('artemisApp.metis.and') + afterLastComma;
            }
            return listOfReactingUsers;
        }
    }
}

import { OnDestroy, Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { map, Observable, Subscription, tap } from 'rxjs';

export const USER_COUNT_LIMIT = 10;
export const PLACEHOLDER_USER_REACTED = 'REPLACE_WITH_TRANSLATED_YOU';

/**
 * Pipe to show list of reacting users when hovering over emojis.
 * This pipe is stateful (pure = false) so that it can adapt to changes of the current locale.
 */
@Pipe({
    name: 'reactingUsersOnPosting',
    pure: false,
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    private reactingUsersString: string;

    constructor(private translateService: TranslateService) {}

    /**
     * Transforms a given name list of reacting users to a prosaic string
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {string} concatenated (and shortened if required) list of reacting users
     */
    transform(reactingUsers: string[]): Observable<string> {
        return this.translateService.onLangChange.pipe(
            map((event: LangChangeEvent) => {
                return this.updateReactingUsersString(reactingUsers);
            }),
        );
    }

    /**
     * Manipulates the `reactingUsersString` variable taking into account the language, if the string has to be stripped,
     * and if the currently logged in user is addressed directly ('you' instead of name)
     * @param {string[]} reactingUsers
     * @private
     */
    updateReactingUsersString(reactingUsers: string[]): string {
        // determine if the list includes the currently logged in user
        if (reactingUsers.includes(PLACEHOLDER_USER_REACTED)) {
            if (reactingUsers.length === 1) {
                // set "you" as ready-to-use reacting users string
                this.reactingUsersString = this.translateService.instant('artemisApp.metis.you');
                return this.reactingUsersString;
            }
            // if more than the currently logged in user reacted,
            // remove placeholder and replace it with directly addressing currently logged in user
            reactingUsers = reactingUsers.filter((user) => user !== PLACEHOLDER_USER_REACTED);
            reactingUsers = [this.translateService.instant('artemisApp.metis.you')].concat(reactingUsers);
        }
        // determine if list has to be trimmed
        const numberOfReactingUsers = reactingUsers.length;
        if (numberOfReactingUsers > USER_COUNT_LIMIT) {
            // prepare trimmed list
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT);
            this.reactingUsersString =
                reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltipTrimmed', { number: numberOfReactingUsers - USER_COUNT_LIMIT });
        } else {
            // prepare list
            this.reactingUsersString = reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltip');
            // replace last comma by "and"
            const lastCommaIndex = this.reactingUsersString.lastIndexOf(',');
            if (lastCommaIndex > -1) {
                const beforeLastComma = this.reactingUsersString.substring(0, lastCommaIndex);
                const afterLastComma = this.reactingUsersString.substring(lastCommaIndex + 2, this.reactingUsersString.length);
                this.reactingUsersString = beforeLastComma + this.translateService.instant('artemisApp.metis.and') + afterLastComma;
            }
        }
        return this.reactingUsersString;
    }
}

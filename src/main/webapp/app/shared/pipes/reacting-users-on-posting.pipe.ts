import { Pipe, PipeTransform } from '@angular/core';
import { LangChangeEvent, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';

export const USER_COUNT_LIMIT = 10;
export const PLACEHOLDER_USER_REACTED = 'REPLACE_WITH_TRANSLATED_YOU';

@Pipe({
    name: 'reactingUsersOnPosting',
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    private onLangChange?: Subscription;

    constructor(private translateService: TranslateService) {}

    /**
     * Converts markdown used in posting content into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {string} concatenated (and shortened if required) list of reacting users
     */
    transform(reactingUsers: string[]): string {
        // Subscribe to onLangChange event, in case the language changes.
        if (!this.onLangChange) {
            this.onLangChange = this.translateService.onLangChange.subscribe((event: LangChangeEvent) => {
                this.translateService.use(event.lang);
            });
        }

        let reactingUsersString: string;
        if (reactingUsers.includes(PLACEHOLDER_USER_REACTED)) {
            // remove placeholder
            reactingUsers = reactingUsers.filter((user) => user !== PLACEHOLDER_USER_REACTED);
            reactingUsers = [this.translateService.instant('artemisApp.metis.you')].concat(reactingUsers);
        }
        const numberOfReactingUsers = reactingUsers.length;
        if (numberOfReactingUsers > USER_COUNT_LIMIT) {
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT);
            reactingUsersString =
                reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltipTrimmed', { number: numberOfReactingUsers - USER_COUNT_LIMIT });
        } else {
            reactingUsersString = reactingUsers.join(', ') + this.translateService.instant('artemisApp.metis.reactedTooltip');
            // replace last comma by "and"
            const lastCommaIndex = reactingUsersString.lastIndexOf(',');
            if (lastCommaIndex > -1) {
                const beforeLastComma = reactingUsersString.substring(0, lastCommaIndex);
                const afterLastComma = reactingUsersString.substring(lastCommaIndex + 2, reactingUsersString.length);
                return beforeLastComma + this.translateService.instant('artemisApp.metis.and') + afterLastComma;
            }
        }
        return reactingUsersString;
    }

    private cleanUpSubscription(): void {
        if (this.onLangChange != undefined) {
            this.onLangChange.unsubscribe();
            this.onLangChange = undefined;
        }
    }

    /**
     * Unsubscribe from onLangChange event of translation service on pipe destruction.
     */
    ngOnDestroy(): void {
        this.cleanUpSubscription();
    }
}

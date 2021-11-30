import { ChangeDetectorRef, Pipe, PipeTransform } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export const USER_COUNT_LIMIT = 10;
export const PLACEHOLDER_USER_REACTED = 'REPLACE_WITH_TRANSLATED_YOU';

@Pipe({
    name: 'reactingUsersOnPosting',
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    private artemisTranslatePipe: ArtemisTranslatePipe;
    constructor(private translateService: TranslateService, private changeDetectorRef: ChangeDetectorRef) {
        this.artemisTranslatePipe = new ArtemisTranslatePipe(translateService, changeDetectorRef);
    }

    /**
     * Converts markdown used in posting content into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {string} concatenated (and shortened if required) list of reacting users
     */
    transform(reactingUsers: string[]): string {
        let reactingUsersString: string;
        if (reactingUsers.includes(PLACEHOLDER_USER_REACTED)) {
            // remove placeholder
            reactingUsers = reactingUsers.filter((user) => user !== PLACEHOLDER_USER_REACTED);
            reactingUsers = [this.artemisTranslatePipe.transform('artemisApp.metis.you')].concat(reactingUsers);
        }
        const numberOfReactingUsers = reactingUsers.length;
        if (numberOfReactingUsers > USER_COUNT_LIMIT) {
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT);
            reactingUsersString =
                reactingUsers.join(', ') + this.artemisTranslatePipe.transform('artemisApp.metis.reactedTooltipTrimmed', { number: numberOfReactingUsers - USER_COUNT_LIMIT });
        } else {
            reactingUsersString = reactingUsers.join(', ') + this.artemisTranslatePipe.transform('artemisApp.metis.reactedTooltip');
            // replace last comma by "and"
            const lastCommaIndex = reactingUsersString.lastIndexOf(',');
            if (lastCommaIndex > -1) {
                const beforeLastComma = reactingUsersString.substring(0, lastCommaIndex);
                const afterLastComma = reactingUsersString.substring(lastCommaIndex + 2, reactingUsersString.length);
                return beforeLastComma + this.artemisTranslatePipe.transform('artemisApp.metis.and') + afterLastComma;
            }
        }
        return reactingUsersString;
    }
}

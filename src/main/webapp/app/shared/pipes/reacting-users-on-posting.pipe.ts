import { Pipe, PipeTransform } from '@angular/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

const USER_COUNT_LIMIT = 10;

@Pipe({
    name: 'reactingUsersOnPosting',
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    constructor(private artemisTranslate: ArtemisTranslatePipe) {}
    /**
     * Converts markdown used in posting content into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {string} concatenated, shortened if required
     */
    transform(reactingUsers: string[]): string {
        let reactingUsersString: string;
        if (reactingUsers.includes('REPLACE_WITH_TRANSLATED_YOU')) {
            // remove placeholder
            reactingUsers = reactingUsers.filter((user) => user !== 'REPLACE_WITH_TRANSLATED_YOU');
            reactingUsers = [this.artemisTranslate.transform('artemisApp.metis.you')].concat(reactingUsers);
        }
        if (reactingUsers.length > USER_COUNT_LIMIT) {
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT - 1);
            reactingUsersString =
                reactingUsers.join(', ') + this.artemisTranslate.transform('artemisApp.metis.reactedTooltipTrimmed', { number: reactingUsers.length - USER_COUNT_LIMIT });
        } else {
            reactingUsersString = reactingUsers.join(', ') + this.artemisTranslate.transform('artemisApp.metis.reactedTooltip');
        }
        // replace last comma by and
        let lastComma = reactingUsersString.lastIndexOf(',');
        let beforeLastComma = reactingUsersString.substring(0, lastComma);
        let afterLastComma = reactingUsersString.substring(lastComma + reactingUsersString.length);

        return beforeLastComma + this.artemisTranslate.transform('artemisApp.metis.and') + afterLastComma;
    }
}

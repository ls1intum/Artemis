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
        if (reactingUsers.length > USER_COUNT_LIMIT) {
            reactingUsers = reactingUsers.slice(0, USER_COUNT_LIMIT - 1);
            return reactingUsers.join(', ') + this.artemisTranslate.transform('artemisApp.metis.reactedTooltipTrimmed', { number: reactingUsers.length - USER_COUNT_LIMIT });
        } else {
            return reactingUsers.join(', ') + this.artemisTranslate.transform('artemisApp.metis.reactedTooltip');
        }
    }
}

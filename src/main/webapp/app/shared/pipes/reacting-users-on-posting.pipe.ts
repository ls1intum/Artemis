import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'reactingUsersOnPosting',
})
export class ReactingUsersOnPostingPipe implements PipeTransform {
    /**
     * Converts markdown used in posting content into html, sanitizes it and then declares it as safe to bypass further security.
     * @param {string[]} reactingUsers users that are reacting with a certain emoji on a posting
     * @returns {string} concatenated, shortened if required
     */
    transform(reactingUsers: string[]): string {
        let reactingUsersString = '';
        if (reactingUsers.length > 10) {
            reactingUsers = reactingUsers.slice(0, 9);
            reactingUsersString = reactingUsers.join(', ') + ' and ' + (reactingUsers.length - 10) + 'user reacted.';
        } else {
            reactingUsersString = reactingUsers.join(', ') + ' reacted.';
        }
        return reactingUsersString;
    }
}

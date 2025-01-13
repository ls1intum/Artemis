import { Pipe, PipeTransform } from '@angular/core';

/**
 * A pipe that removes the seconds information from a time string in the format '14:00:00'.
 */
@Pipe({ name: 'removeSeconds' })
export class RemoveSecondsPipe implements PipeTransform {
    /**
     * Transforms a time string in the format '14:00:00' to a string without seconds, e.g. '14:00'.
     * @param time The time string to transform.
     * @returns The time string without seconds.
     */
    transform(time: string | undefined): string {
        if (!time) {
            return '';
        }
        // if already in the format '14:00', return it
        if (time.length === 5) {
            return time;
        }

        return time.split(':').slice(0, 2).join(':');
    }
}

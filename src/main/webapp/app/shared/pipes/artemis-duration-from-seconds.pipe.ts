import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({ name: 'artemisDurationFromSeconds' })
export class ArtemisDurationFromSecondsPipe implements PipeTransform {
    /**
     * Convert seconds to a human-readable duration format.
     * @param seconds {number}
     */
    transform(seconds: number): string {
        return moment.utc(seconds * 1000).format('HH:mm:ss');
    }
}

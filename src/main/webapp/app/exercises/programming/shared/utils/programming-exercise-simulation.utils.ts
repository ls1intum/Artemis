import { Injectable } from '@angular/core';

/**
 * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
 */

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSimulationUtils {
    constructor() {}

    /**
     * Checks if the url includes the string "artemislocalhost', which is an indication
     * that the particular programming exercise is not connected to a version control and continuous integration server
     * @param urlToCheck the url which will be checked if it contains the substring
     */
    noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck: string): boolean {
        return urlToCheck.includes('artemislocalhost');
    }
}

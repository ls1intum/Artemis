import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ProgrammingExerciseSimulationUtils {
    /**
     * Checks if the url includes the string "nolocalsetup', which is an indication
     * that the particular programming exercise has no local setup
     * @param urlToCheck the url which will be check if it contains the substring
     */
    hasNoLocalSetup(urlToCheck: string): boolean {
        return urlToCheck.includes('nolocalsetup');
    }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { Language } from 'app/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

/**
 * Common English function words for the lightweight English-vs-German language heuristic in
 * {@link TextEditorService.predictLanguage}. Words shared with German (e.g. "an", "in") appear in
 * both sets so they cancel out in the comparison.
 */
const ENGLISH_STOP_WORDS = new Set([
    'the',
    'a',
    'an',
    'and',
    'or',
    'is',
    'are',
    'was',
    'were',
    'be',
    'been',
    'to',
    'of',
    'in',
    'on',
    'at',
    'for',
    'with',
    'as',
    'by',
    'from',
    'that',
    'this',
    'these',
    'those',
    'it',
    'he',
    'she',
    'they',
    'we',
    'you',
    'i',
    'his',
    'her',
    'their',
    'its',
    'not',
    'but',
    'also',
    'which',
    'who',
    'what',
    'when',
    'where',
    'there',
    'here',
    'about',
    'into',
    'over',
    'after',
    'before',
]);

/** Common German function words for the same heuristic. */
const GERMAN_STOP_WORDS = new Set([
    'der',
    'die',
    'das',
    'dass',
    'ein',
    'eine',
    'einer',
    'eines',
    'einem',
    'einen',
    'und',
    'oder',
    'ist',
    'sind',
    'war',
    'waren',
    'ich',
    'du',
    'er',
    'sie',
    'wir',
    'ihr',
    'nicht',
    'sich',
    'mit',
    'auch',
    'für',
    'an',
    'am',
    'den',
    'dem',
    'des',
    'von',
    'vom',
    'zu',
    'zum',
    'zur',
    'im',
    'in',
    'auf',
    'als',
    'aus',
    'bei',
    'nach',
    'über',
    'unter',
    'durch',
    'um',
    'noch',
    'nur',
    'sehr',
    'schon',
    'wie',
    'wenn',
    'weil',
    'aber',
    'sowie',
    'zudem',
]);

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    private http = inject(HttpClient);

    /**
     * Retrieves the participation data for the text editor.
     * @param participationId - The ID of the participation
     * @param resultId - Optional ID of a specific result to retrieve; if not provided, returns the latest result
     * @returns Observable of the student participation with exercise data
     */
    get(participationId: number, resultId?: number): Observable<StudentParticipation> {
        const params = resultId ? { resultId: resultId.toString() } : undefined;
        return this.http
            .get<StudentParticipation>(`api/text/participations/${participationId}/text-editor`, { params })
            .pipe(tap((participation: StudentParticipation) => ExerciseService.convertExerciseDatesFromServer(participation.exercise)));
    }

    /**
     * Predicts whether the given text is English or German using a lightweight function-word
     * (stop-word) vote. This is an advisory, non-blocking hint used to pre-fill a submission's
     * language; it is not a general-purpose language detector. Returns undefined when the text is
     * neither clearly English nor German (e.g. another language, or no decisive signal).
     * @param   {string} text
     *
     * @returns {Language} predicted language of the text, or undefined
     */
    predictLanguage(text: string): Language | undefined {
        const words = text.toLowerCase().match(/\p{L}+/gu);
        if (!words) {
            return undefined;
        }

        let englishScore = 0;
        let germanScore = 0;
        for (const word of words) {
            if (ENGLISH_STOP_WORDS.has(word)) {
                englishScore++;
            }
            if (GERMAN_STOP_WORDS.has(word)) {
                germanScore++;
            }
        }

        if (germanScore > englishScore) {
            return Language.GERMAN;
        }
        if (englishScore > germanScore) {
            return Language.ENGLISH;
        }
        return undefined;
    }
}

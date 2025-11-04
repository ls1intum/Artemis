import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { francAll } from 'franc-min';
import { Language } from 'app/core/course/shared/entities/course.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    private http = inject(HttpClient);

    get(participationId: number): Observable<StudentParticipation> {
        const params = { withAthenaConfig: 'true' };
        return this.http
            .get(`api/text/text-editor/${participationId}`, { responseType: 'json', params })
            .pipe(tap((participation: StudentParticipation) => ExerciseService.convertExerciseDatesFromServer(participation.exercise)));
    }

    /**
     * Takes a text and returns its language
     * @param   {string} text
     *
     * @returns {Language} language of the text
     */
    predictLanguage(text: string): Language | undefined {
        const languageProbabilities = francAll(text);

        switch (languageProbabilities[0][0]) {
            case 'eng':
                return Language.ENGLISH;

            case 'deu':
                return Language.GERMAN;
        }
    }
}

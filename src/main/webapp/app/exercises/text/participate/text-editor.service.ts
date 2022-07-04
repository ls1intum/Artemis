import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { francAll } from 'franc-min';
import { Language } from 'app/entities/tutor-group.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    constructor(private http: HttpClient) {}

    get(participationId: number): Observable<StudentParticipation> {
        return this.http.get(`api/text-editor/${participationId}`, { responseType: 'json' });
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

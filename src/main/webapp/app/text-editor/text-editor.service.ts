import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { Language } from 'app/entities/tutor-group';
import { Franc, FrancLanguage, SUPPORTED_TEXT_LANGUAGES } from './franc';

@Injectable({ providedIn: 'root' })
export class TextEditorService {
    readonly franc: Franc = require('franc-min');

    constructor(private http: HttpClient) {}

    get(id: number): Observable<any> {
        return this.http.get(`api/text-editor/${id}`, { responseType: 'json' });
    }

    predictLanguage(text: string): Language | null {
        const languageProbabilities = this.franc.all(text, { only: SUPPORTED_TEXT_LANGUAGES });

        switch (languageProbabilities[0][0]) {
            case FrancLanguage.ENGLISH:
                return Language.ENGLISH;

            case FrancLanguage.GERMAN:
                return Language.GERMAN;

            case FrancLanguage.UNDEFINED:
            default:
                return null;
        }
    }
}

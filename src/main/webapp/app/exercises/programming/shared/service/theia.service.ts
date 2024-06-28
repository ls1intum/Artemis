import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { ProgrammingLanguage } from 'app/entities/programming-exercise.model';

@Injectable({ providedIn: 'root' })
export class TheiaService {
    private resourceUrl = 'api/theia';

    constructor(private http: HttpClient) {}

    /**
     * Fetches the theia images for the given programming language
     * @param {ProgrammingLanguage} language
     * @returns the theia images or undefined if no images are available for this language
     */
    getTheiaImages(language: ProgrammingLanguage): Observable<any> {
        return this.http.get<any>(`${this.resourceUrl}/images`, {
            params: {
                language: language,
            },
        });
    }
}

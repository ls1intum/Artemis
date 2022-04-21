import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CodeHint } from 'app/entities/hestia/code-hint-model';
import { createRequestOption } from 'app/shared/util/request.util';

export interface ICodeHintService {
    /**
     * Generates the code hints for a programming exercise
     * @param exerciseId Id of the programming exercise
     * @param deleteOldCodeHints Whether the old code hints should be deleted
     */
    generateCodeHintsForExercise(exerciseId: number, deleteOldCodeHints: boolean): Observable<HttpResponse<CodeHint[]>>;
}

@Injectable({ providedIn: 'root' })
export class CodeHintService implements ICodeHintService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(protected http: HttpClient) {}

    /**
     * Generates the code hints for a programming exercise
     * @param exerciseId Id of the programming exercise
     * @param deleteOldCodeHints Whether the old code hints should be deleted
     */
    generateCodeHintsForExercise(exerciseId: number, deleteOldCodeHints: boolean): Observable<HttpResponse<CodeHint[]>> {
        const options = createRequestOption({ deleteOldCodeHints });
        return this.http.post<CodeHint[]>(`${this.resourceUrl}/${exerciseId}/code-hints`, undefined, {
            params: options,
            observe: 'response',
        });
    }
}

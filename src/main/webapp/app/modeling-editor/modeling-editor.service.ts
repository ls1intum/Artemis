import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { ModelingSubmission } from 'app/entities/modeling-submission';

@Injectable({ providedIn: 'root' })
export class ModelingEditorService {
    constructor(private http: HttpClient) {}

    get(participationId: number): Observable<ModelingSubmission> {
        return this.http.get<ModelingSubmission>(`api/modeling-editor/${participationId}`, { responseType: 'json' });
    }
}

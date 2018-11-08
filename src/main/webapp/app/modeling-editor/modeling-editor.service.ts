import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

@Injectable({ providedIn: 'root' })
export class ModelingEditorService {
    constructor(private http: HttpClient) {}

    get(id: number): Observable<any> {
        return this.http.get(`api/modeling-editor/${id}`, { responseType: 'json' });
    }
}

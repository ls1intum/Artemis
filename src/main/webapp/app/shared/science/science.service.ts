import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScienceEventType } from 'app/shared/science/science.model';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}
    logEvent(type: ScienceEventType): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/science`, type, { observe: 'response' });
    }
}

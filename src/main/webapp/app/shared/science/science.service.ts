import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ScienceEventType } from 'app/shared/science/science.model';

@Injectable({ providedIn: 'root' })
export class ScienceService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}
    logEvent(type: ScienceEventType): void {
        this.httpClient.put(`${this.resourceURL}/science`, type);
    }
}

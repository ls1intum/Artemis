import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class HealthService {
    separator: string;

    constructor(private http: HttpClient) {
        this.separator = '.';
    }

    checkHealth(): Observable<any> {
        return this.http.get(SERVER_API_URL + 'management/health');
    }
}

import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class HealthService {
    private http = inject(HttpClient);

    separator: string;

    constructor() {
        this.separator = '.';
    }

    checkHealth(): Observable<any> {
        return this.http.get('management/health');
    }
}

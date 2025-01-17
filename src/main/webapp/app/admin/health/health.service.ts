import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Health } from 'app/admin/health/health.model';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class HealthService {
    private http = inject(HttpClient);

    checkHealth(): Observable<Health> {
        return this.http.get<Health>('management/health');
    }
}

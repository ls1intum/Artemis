import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { SERVER_API_URL } from 'app/app.constants';

export type HealthStatus = 'UP' | 'DOWN' | 'UNKNOWN' | 'OUT_OF_SERVICE';

export type HealthKey = 'continuousIntegrationServer' | 'versionControlServer' | 'diskSpace' | 'mail' | 'ping' | 'db';

export interface Health {
    status: HealthStatus;
    components: {
        [key in HealthKey]?: HealthDetails;
    };
}

export interface HealthDetails {
    status: HealthStatus;
    details: any;
}

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

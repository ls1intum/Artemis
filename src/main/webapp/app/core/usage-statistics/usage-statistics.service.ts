import { HttpClient } from '@angular/common/http';
import { Service, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { PublicUsageStatistics } from 'app/core/usage-statistics/usage-statistics.model';

@Service()
export class UsageStatisticsService {
    private readonly http = inject(HttpClient);

    /** Loads the public, anonymous usage numbers. No authentication is required. */
    getUsageStatistics(): Observable<PublicUsageStatistics> {
        return this.http.get<PublicUsageStatistics>('api/core/public/usage-statistics');
    }
}

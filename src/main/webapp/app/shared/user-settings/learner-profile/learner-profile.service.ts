import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface FeedbackLearnerProfile {
    id?: number;
    practicalVsTheoretical: number;
    creativeVsFocused: number;
    followUpVsSummary: number;
    briefVsDetailed: number;
}

@Injectable({ providedIn: 'root' })
export class LearnerProfileService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/feedback-learner-profile';

    /**
     * Get the current user's feedback learner profile
     */
    getProfile(): Observable<FeedbackLearnerProfile> {
        return this.http.get<FeedbackLearnerProfile>(this.resourceUrl);
    }

    /**
     * Update the current user's feedback learner profile
     */
    updateProfile(profile: FeedbackLearnerProfile): Observable<FeedbackLearnerProfile> {
        return this.http.put<FeedbackLearnerProfile>(this.resourceUrl, profile);
    }
}

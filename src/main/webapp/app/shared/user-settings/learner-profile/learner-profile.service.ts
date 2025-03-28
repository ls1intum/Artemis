import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, ReplaySubject } from 'rxjs';

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
    private currentLearnerProfileSubject = new ReplaySubject<FeedbackLearnerProfile>(1);

    constructor() {
        this.loadProfile();
    }

    private loadProfile() {
        this.getProfile().subscribe((profile) => {
            this.currentLearnerProfileSubject.next(profile);
        });
    }

    /**
     * Get updates to the learner profile settings
     */
    getLearnerProfileUpdates(): Observable<FeedbackLearnerProfile> {
        return this.currentLearnerProfileSubject.asObservable();
    }

    /**
     * Initialize the learner profile by loading it from the server
     */
    initialize() {
        this.loadProfile();
    }

    /**
     * Refresh the learner profile settings from the server
     */
    refreshLearnerProfile(): void {
        this.getProfile().subscribe((profile) => {
            this.currentLearnerProfileSubject.next(profile);
        });
    }

    /**
     * Get the current user's feedback learner profile from the server
     * @returns Observable of the user's FeedbackLearnerProfile
     */
    getProfile(): Observable<FeedbackLearnerProfile> {
        return this.http.get<FeedbackLearnerProfile>(this.resourceUrl);
    }

    /**
     * Update the current user's feedback learner profile
     * @param profile the profile to update
     * @returns Observable of the updated FeedbackLearnerProfile
     */
    updateProfile(profile: FeedbackLearnerProfile): Observable<FeedbackLearnerProfile> {
        return this.http.put<FeedbackLearnerProfile>(this.resourceUrl, profile);
    }
}

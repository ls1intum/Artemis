import { Injectable, inject } from '@angular/core';
import { LearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { Observable } from 'rxjs';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class LearnerProfileApiService extends BaseApiHttpService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/atlas/learner-profiles';

    getLearnerProfileForCurrentUser(): Observable<LearnerProfileDTO> {
        return this.http.get<LearnerProfileDTO>(`${this.resourceUrl}`);
    }

    putUpdatedLearnerProfile(learnerProfile: LearnerProfileDTO): Observable<LearnerProfileDTO> {
        return this.http.put<LearnerProfileDTO>(`${this.resourceUrl}/${learnerProfile.id}`, learnerProfile);
    }
}

import { Injectable } from '@angular/core';
import { CourseLearnerProfileDTO } from 'app/core/learner-profile/shared/entities/learner-profile.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class LearnerProfileApiService extends BaseApiHttpService {
    async getCourseLearnerProfilesForCurrentUser(): Promise<CourseLearnerProfileDTO[]> {
        return await this.get<CourseLearnerProfileDTO[]>('atlas/course-learner-profiles');
    }

    putUpdatedCourseLearnerProfile(courseLearnerProfile: CourseLearnerProfileDTO): Promise<CourseLearnerProfileDTO> {
        return this.put<CourseLearnerProfileDTO>(`atlas/course-learner-profiles/${courseLearnerProfile.id}`, courseLearnerProfile);
    }
}

import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CourseLearnerProfileDTO } from 'app/entities/learner-profile.model';

@Injectable({ providedIn: 'root' })
export class LearnerProfileApiService extends BaseApiHttpService {
    async getCourseLearnerProfilesForCurrentUser(): Promise<Record<number, CourseLearnerProfileDTO>> {
        return await this.get<Record<number, CourseLearnerProfileDTO>>('atlas/course-learner-profiles');
    }

    putUpdatedCourseLearnerProfile(courseLearnerProfile: CourseLearnerProfileDTO): Promise<CourseLearnerProfileDTO> {
        return this.put<CourseLearnerProfileDTO>(`atlas/course-learner-profiles/${courseLearnerProfile.id}`, courseLearnerProfile);
    }
}

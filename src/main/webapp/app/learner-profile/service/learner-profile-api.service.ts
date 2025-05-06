import { Injectable } from '@angular/core';
import { CourseLearnerProfileDTO, LearnerProfileDTO } from 'app/learner-profile/shared/entities/learner-profile.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class LearnerProfileApiService extends BaseApiHttpService {
    async getCourseLearnerProfilesForCurrentUser(): Promise<Record<number, CourseLearnerProfileDTO>> {
        return await this.get<Record<number, CourseLearnerProfileDTO>>('atlas/course-learner-profiles');
    }

    putUpdatedCourseLearnerProfile(courseLearnerProfile: CourseLearnerProfileDTO): Promise<CourseLearnerProfileDTO> {
        return this.put<CourseLearnerProfileDTO>(`atlas/course-learner-profiles/${courseLearnerProfile.id}`, courseLearnerProfile);
    }

    async getLearnerProfileForCurrentUser(): Promise<LearnerProfileDTO> {
        return await this.get<LearnerProfileDTO>('atlas/learner-profiles');
    }

    putUpdatedLearnerProfile(learnerProfile: LearnerProfileDTO): Promise<LearnerProfileDTO> {
        return this.put<LearnerProfileDTO>(`atlas/learner-profiles/${learnerProfile.id}`, learnerProfile);
    }
}

import { Injectable } from '@angular/core';
import { CourseLearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/course-learner-profile-dto.model';
import { LearnerProfileDTO } from 'app/core/user/settings/learner-profile/dto/learner-profile-dto.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class LearnerProfileApiService extends BaseApiHttpService {
    async getCourseLearnerProfilesForCurrentUser(): Promise<CourseLearnerProfileDTO[]> {
        return await this.get<CourseLearnerProfileDTO[]>('atlas/course-learner-profiles');
    }

    putUpdatedCourseLearnerProfile(courseLearnerProfile: CourseLearnerProfileDTO): Promise<CourseLearnerProfileDTO> {
        return this.put<CourseLearnerProfileDTO>(`atlas/course-learner-profiles/${courseLearnerProfile.id}`, courseLearnerProfile);
    }

    async getLearnerProfileForCurrentUser(): Promise<LearnerProfileDTO> {
        return await this.get<LearnerProfileDTO>('atlas/learner-profile');
    }

    putUpdatedLearnerProfile(learnerProfile: LearnerProfileDTO): Promise<LearnerProfileDTO> {
        return this.put<LearnerProfileDTO>(`atlas/learner-profile`, learnerProfile);
    }
}

import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    CourseFeatureBonusSettings,
    FeatureBonusAward,
    FeatureBonusAwardRequest,
    ParticipationFeatureBonus,
    StudentFeatureBonus,
} from 'app/assessment/shared/entities/feature-bonus.model';

/**
 * Feature bonus: points tutors award for additional features while assessing, converted by the course into a bonus on the final grade.
 */
@Service()
export class FeatureBonusService {
    private readonly http = inject(HttpClient);

    private readonly resourceUrl = 'api/assessment';

    getAwards(participationId: number): Observable<FeatureBonusAward[]> {
        return this.http.get<FeatureBonusAward[]>(`${this.resourceUrl}/participations/${participationId}/feature-bonus-awards`);
    }

    createAward(participationId: number, request: FeatureBonusAwardRequest): Observable<FeatureBonusAward> {
        return this.http.post<FeatureBonusAward>(`${this.resourceUrl}/participations/${participationId}/feature-bonus-awards`, request);
    }

    updateAward(participationId: number, awardId: number, request: FeatureBonusAwardRequest): Observable<FeatureBonusAward> {
        return this.http.put<FeatureBonusAward>(`${this.resourceUrl}/participations/${participationId}/feature-bonus-awards/${awardId}`, request);
    }

    deleteAward(participationId: number, awardId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/feature-bonus-awards/${awardId}`);
    }

    getFeatureBonusOfExercise(exerciseId: number): Observable<ParticipationFeatureBonus[]> {
        return this.http.get<ParticipationFeatureBonus[]>(`${this.resourceUrl}/exercises/${exerciseId}/feature-bonus`);
    }

    getSettings(courseId: number): Observable<CourseFeatureBonusSettings> {
        return this.http.get<CourseFeatureBonusSettings>(`${this.resourceUrl}/courses/${courseId}/feature-bonus-settings`);
    }

    getFeatureBonusOfCourse(courseId: number): Observable<StudentFeatureBonus[]> {
        return this.http.get<StudentFeatureBonus[]>(`${this.resourceUrl}/courses/${courseId}/feature-bonus`);
    }
}

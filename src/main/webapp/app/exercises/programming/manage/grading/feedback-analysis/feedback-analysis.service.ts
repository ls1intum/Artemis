import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    taskNumber: number;
}

@Injectable()
export class FeedbackAnalysisService extends BaseApiHttpService {
    private readonly EXERCISE_RESOURCE_URL = 'exercises';

    getFeedbackDetailsForExercise(exerciseId: number): Promise<FeedbackDetail[]> {
        return this.get<FeedbackDetail[]>(`${this.EXERCISE_RESOURCE_URL}/${exerciseId}/feedback-details`);
    }
}

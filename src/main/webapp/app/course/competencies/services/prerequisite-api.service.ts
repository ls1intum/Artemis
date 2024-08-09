import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

@Injectable({
    providedIn: 'root',
})
export class PrerequisiteApiService extends BaseApiHttpService {
    async deletePrerequisite(courseId: number, prerequisiteId: number): Promise<void> {
        return await this.delete<void>(`courses/${courseId}/competencies/prerequisites/${prerequisiteId}`);
    }
}

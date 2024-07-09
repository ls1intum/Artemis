import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { Competency } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CompetencyApiService extends BaseApiHttpService {
    async getAllByCourseId(courseId: number): Promise<Competency[]> {
        return this.get<Competency[]>(`courses/${courseId}/competencies`);
    }
}

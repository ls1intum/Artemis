import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { Prerequisite } from 'app/entities/prerequisite.model';

@Injectable({
    providedIn: 'root',
})
export class PrerequisiteApiService extends BaseApiHttpService {
    async getPrerequisitesByCourseId(courseId: number): Promise<Prerequisite[]> {
        return this.get<Prerequisite[]>(`courses/${courseId}/competencies/prerequisites`);
    }

    async getPrerequisiteById(courseId: number, prerequisiteId: number): Promise<Prerequisite> {
        return this.get<Prerequisite>(`courses/${courseId}/competencies/prerequisites/${prerequisiteId}`);
    }
}

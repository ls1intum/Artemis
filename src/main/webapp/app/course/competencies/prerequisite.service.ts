import { Prerequisite, PrerequisiteResponseDTO } from 'app/entities/prerequisite.model';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { CourseCompetency } from 'app/entities/competency.model';
import { convertDateFromServer } from 'app/utils/date.utils';

@Injectable({
    providedIn: 'root',
})
export class PrerequisiteService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    getAllPrerequisitesForCourse(courseId: number): Observable<Prerequisite[]> {
        return this.httpClient
            .get<PrerequisiteResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites`, { observe: 'response' })
            .pipe(map((resp) => resp.body!.map((prerequisiteDTO) => PrerequisiteService.convertResponseDTOToPrerequisite(prerequisiteDTO))));
        //TODO: send title to entityTitleService when we allow prerequisite detail view.
    }

    deletePrerequisite(prerequisiteId: number, courseId: number) {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites/${prerequisiteId}`, { observe: 'response' });
    }

    importPrerequisites(prerequisiteIds: number[], courseId: number): Observable<Prerequisite[]> {
        return this.httpClient
            .post<PrerequisiteResponseDTO[]>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites/import`, prerequisiteIds, { observe: 'response' })
            .pipe(map((resp) => resp.body!.map((prerequisiteDTO) => PrerequisiteService.convertResponseDTOToPrerequisite(prerequisiteDTO))));
    }

    /**
     * Converts a PrerequisiteResponseDTO to a Prerequisite
     * It converts the softDueDate to dayjs and the linkedCourseCompetencyDTO to a CourseCompetency
     * @param prerequisiteDTO PrerequisiteResponseDTO
     * @return the Prerequisite
     */
    private static convertResponseDTOToPrerequisite(prerequisiteDTO: PrerequisiteResponseDTO): Prerequisite {
        let linkedCourseCompetency: CourseCompetency | undefined = undefined;
        const softDueDate = convertDateFromServer(prerequisiteDTO.softDueDate);

        const linkedCourseCompetencyDTO = prerequisiteDTO.linkedCourseCompetencyDTO;
        if (linkedCourseCompetencyDTO) {
            linkedCourseCompetency = {
                id: linkedCourseCompetencyDTO.id,
                course: {
                    id: linkedCourseCompetencyDTO.courseId,
                    title: linkedCourseCompetencyDTO.courseTitle,
                    semester: linkedCourseCompetencyDTO.semester,
                },
            };
        }

        return {
            id: prerequisiteDTO.id,
            title: prerequisiteDTO.title,
            description: prerequisiteDTO.description,
            masteryThreshold: prerequisiteDTO.masteryThreshold,
            optional: prerequisiteDTO.optional,
            taxonomy: prerequisiteDTO.taxonomy,
            softDueDate: softDueDate,
            linkedCourseCompetency: linkedCourseCompetency,
        };
    }
}

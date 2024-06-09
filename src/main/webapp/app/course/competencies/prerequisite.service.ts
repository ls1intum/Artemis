import { Prerequisite, PrerequisiteRequestDTO, PrerequisiteResponseDTO } from 'app/entities/prerequisite.model';
import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { CourseCompetency, DEFAULT_MASTERY_THRESHOLD } from 'app/entities/competency.model';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

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

    getPrerequisite(prerequisiteId: number, courseId: number) {
        return this.httpClient
            .get<PrerequisiteResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites/${prerequisiteId}`, { observe: 'response' })
            .pipe(map((resp) => PrerequisiteService.convertResponseDTOToPrerequisite(resp.body!)));
    }

    createPrerequisite(prerequisite: Prerequisite, courseId: number): Observable<Prerequisite | undefined> {
        const prerequisiteDTO = this.convertToRequestDTO(prerequisite);
        return this.httpClient
            .post<PrerequisiteResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites`, prerequisiteDTO, { observe: 'response' })
            .pipe(map((resp) => PrerequisiteService.convertResponseDTOToPrerequisite(resp.body!)));
    }

    updatePrerequisite(prerequisite: Prerequisite, prerequisiteId: number, courseId: number): Observable<Prerequisite | undefined> {
        const prerequisiteDTO = this.convertToRequestDTO(prerequisite);
        return this.httpClient
            .put<PrerequisiteResponseDTO>(`${this.resourceURL}/courses/${courseId}/competencies/prerequisites/${prerequisiteId}`, prerequisiteDTO, { observe: 'response' })
            .pipe(map((resp) => PrerequisiteService.convertResponseDTOToPrerequisite(resp.body!)));
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
     * Converts a Prerequisite to a PrerequisiteRequestDTO and converts the date to a string
     * @param prerequisite the prerequisite to convert
     * @return the PrerequisiteRequestDTO
     */
    convertToRequestDTO(prerequisite: Prerequisite) {
        const dto: PrerequisiteRequestDTO = {
            title: prerequisite.title,
            description: prerequisite.description,
            taxonomy: prerequisite.taxonomy,
            masteryThreshold: prerequisite.masteryThreshold ?? DEFAULT_MASTERY_THRESHOLD,
            optional: prerequisite.optional,
            softDueDate: convertDateFromClient(prerequisite.softDueDate),
        };
        return dto;
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

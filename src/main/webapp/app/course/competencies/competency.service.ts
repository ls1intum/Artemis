import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    Competency,
    CompetencyImportResponseDTO,
    CompetencyJol,
    CompetencyProgress,
    CompetencyRelation,
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetencyProgress,
} from 'app/entities/competency.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map, tap } from 'rxjs/operators';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { AccountService } from 'app/core/auth/account.service';
import { CompetencyPageableSearch, SearchResult } from 'app/shared/table/pageable-table';

type EntityResponseType = HttpResponse<Competency>;
type EntityArrayResponseType = HttpResponse<Competency[]>;

@Injectable({
    providedIn: 'root',
})
export class CompetencyService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private entityTitleService: EntityTitleService,
        private lectureUnitService: LectureUnitService,
        private accountService: AccountService,
    ) {}

    getForImport(pageable: CompetencyPageableSearch) {
        const params = this.createCompetencySearchHttpParams(pageable);
        return this.httpClient
            .get(`${this.resourceURL}/competencies/for-import`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<SearchResult<Competency>>) => resp && resp.body!));
    }

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies`, { observe: 'response' }).pipe(
            map((res: EntityArrayResponseType) => CompetencyService.convertArrayResponseDatesFromServer(res)),
            tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
        );
    }

    /**
     * Sets the judgment of learning for a competency.
     *
     * @param courseId The ID of the course.
     * @param competencyId The ID of the competency.
     * @param jolValue The judgment of learning value.
     * @returns An Observable of the HTTP response.
     */
    setJudgementOfLearning(courseId: number, competencyId: number, jolValue: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/jol/${jolValue}`, null, { observe: 'response' });
    }

    /**
     * Retrieves the judgement of learning (JoL) values for all competencies of a specified course.
     *
     * @param courseId the id of the course for which the JoL values should be fetched
     * @return an Observable of HttpResponse containing a map from competency id to JoL value
     */
    getJoLAllForCourse(courseId: number): Observable<HttpResponse<{ [key: number]: CompetencyJol }>> {
        return this.httpClient
            .get<{ [key: number]: CompetencyJol }>(`${this.resourceURL}/courses/${courseId}/competencies/jol`, { observe: 'response' })
            .pipe(map((res: HttpResponse<{ [key: number]: CompetencyJol }>) => res));
    }

    getProgress(competencyId: number, courseId: number, refresh = false) {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.httpClient.get<CompetencyProgress>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/student-progress`, {
            params,
            observe: 'response',
        });
    }

    getCourseProgress(competencyId: number, courseId: number) {
        return this.httpClient.get<CourseCompetencyProgress>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/course-progress`, {
            observe: 'response',
        });
    }

    findById(competencyId: number, courseId: number) {
        return this.httpClient.get<Competency>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertCompetencyResponseFromServer(res);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    /**
     * Retrieves the judgement of learning (JoL) value for a specific competency in a course.
     *
     * @param courseId the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to get the JoL value
     * @return an Observable of HttpResponse containing the JoL value or null if not set
     */
    getJoL(courseId: number, competencyId: number): Observable<HttpResponse<CompetencyJol>> {
        return this.httpClient
            .get<CompetencyJol>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/jol`, { observe: 'response' })
            .pipe(map((res: HttpResponse<CompetencyJol>) => res));
    }

    create(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    createBulk(competencies: Competency[], courseId: number) {
        const copy = competencies.map((competency) => this.convertCompetencyFromClient(competency));
        return this.httpClient.post<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies/bulk`, copy, { observe: 'response' });
    }

    update(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    delete(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' });
    }

    import(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const competencyCopy = this.convertCompetencyFromClient(competency);
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies/import`, competencyCopy, { observe: 'response' });
    }

    importBulk(competencies: Competency[], courseId: number, importRelations: boolean) {
        const params = new HttpParams().set('importRelations', importRelations);
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(`${this.resourceURL}/courses/${courseId}/competencies/import/bulk`, competencies, {
            params: params,
            observe: 'response',
        });
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        const params = new HttpParams().set('importRelations', importRelations);
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(`${this.resourceURL}/courses/${courseId}/competencies/import-all/${sourceCourseId}`, null, {
            params: params,
            observe: 'response',
        });
    }

    importStandardizedCompetencies(competencyIdsToImport: number[], courseId: number) {
        return this.httpClient.post<Array<CompetencyImportResponseDTO>>(`${this.resourceURL}/courses/${courseId}/competencies/import-standardized`, competencyIdsToImport, {
            observe: 'response',
        });
    }

    generateCompetenciesFromCourseDescription(courseDescription: string, courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.post<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies/generate-from-description`, courseDescription, { observe: 'response' });
    }

    //prerequisites

    getAllPrerequisitesForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<Competency[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' })
            .pipe(tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    addPrerequisite(competencyId: number, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/prerequisites/${competencyId}`, null, { observe: 'response' });
    }

    removePrerequisite(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${competencyId}`, { observe: 'response' });
    }

    //relations

    createCompetencyRelation(relation: CompetencyRelation, courseId: number) {
        return this.httpClient.post<CompetencyRelation>(`${this.resourceURL}/courses/${courseId}/competencies/relations`, relation, {
            observe: 'response',
        });
    }

    getCompetencyRelations(courseId: number) {
        return this.httpClient.get<CompetencyRelationDTO[]>(`${this.resourceURL}/courses/${courseId}/competencies/relations`, {
            observe: 'response',
        });
    }

    removeCompetencyRelation(competencyRelationId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/relations/${competencyRelationId}`, {
            observe: 'response',
        });
    }

    //helper methods

    /**
     * Creates HttpParams for each field of the given pageable element.
     *
     * @param pageable the CompetencyPageableSearch to create HttpParams for
     * @return the HttpParams
     */
    createCompetencySearchHttpParams(pageable: CompetencyPageableSearch) {
        return new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn)
            .set('title', pageable.title)
            .set('description', pageable.description)
            .set('courseTitle', pageable.courseTitle)
            .set('semester', pageable.semester);
    }

    convertCompetencyResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.softDueDate) {
            res.body.softDueDate = convertDateFromServer(res.body.softDueDate);
        }
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(res.body.lectureUnits);
        }
        if (res.body?.course) {
            this.accountService.setAccessRightsForCourse(res.body.course);
        }
        if (res.body?.exercises) {
            res.body.exercises = ExerciseService.convertExercisesDateFromServer(res.body.exercises);
            res.body.exercises.forEach((exercise) => {
                ExerciseService.parseExerciseCategories(exercise);
                this.accountService.setAccessRightsForExercise(exercise);
            });
        }

        return res;
    }

    convertCompetencyFromClient(competency: Competency): Competency {
        const copy = Object.assign({}, competency, {
            softDueDate: convertDateFromClient(competency.softDueDate),
        });
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromClient(copy.lectureUnits);
        }
        if (copy.exercises) {
            copy.exercises = copy.exercises.map((exercise) => ExerciseService.convertExerciseFromClient(exercise));
        }
        return copy;
    }

    /**
     * Helper methods for date conversion from server and client
     */
    private static convertArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.map((competency: Competency) => (competency.softDueDate = convertDateFromServer(competency.softDueDate)));
        }
        return res;
    }

    private sendTitlesToEntityTitleService(competency: Competency | undefined | null) {
        this.entityTitleService.setTitle(EntityType.COMPETENCY, [competency?.id], competency?.title);
    }
}

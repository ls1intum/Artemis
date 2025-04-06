import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyJol,
    CompetencyProgress,
    CompetencyRelation,
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyProgress,
} from 'app/atlas/shared/entities/competency.model';
import { map, tap } from 'rxjs/operators';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';
import { CompetencyPageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { HttpParams } from '@angular/common/http';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { LectureUnitService } from 'app/lecture/manage/lecture-units/services/lectureUnit.service';
import { AccountService } from 'app/core/auth/account.service';
import { CompetencyRecommendation } from 'app/atlas/manage/generate-competencies/generate-competencies.component';
import { EntityTitleService, EntityType } from 'app/core/navbar/entity-title.service';

type EntityResponseType = HttpResponse<CourseCompetency>;
type EntityArrayResponseType = HttpResponse<CourseCompetency[]>;

type CompetencyJolResponseType = HttpResponse<{
    current: CompetencyJol;
    prior?: CompetencyJol;
}>;
type CompetencyJolMapResponseType = HttpResponse<{
    [key: number]: {
        current: CompetencyJol;
        prior?: CompetencyJol;
    };
}>;

@Injectable({
    providedIn: 'root',
})
export class CourseCompetencyService {
    protected httpClient = inject(HttpClient);
    protected entityTitleService = inject(EntityTitleService);
    protected lectureUnitService = inject(LectureUnitService);
    protected accountService = inject(AccountService);

    protected resourceURL = 'api/atlas';

    getForImport(pageable: CompetencyPageableSearch) {
        const params = this.createCompetencySearchHttpParams(pageable);
        return this.httpClient
            .get(`${this.resourceURL}/course-competencies/for-import`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<SearchResult<CourseCompetency>>) => resp && resp.body!));
    }

    /**
     * Creates HttpParams for each field of the given pageable element.
     *
     * @param pageable the CompetencyPageableSearch to create HttpParams for
     * @return the HttpParams
     */
    private createCompetencySearchHttpParams(pageable: CompetencyPageableSearch) {
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

    getAllForCourse(courseId: number, filtered = false): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<CourseCompetency[]>(`${this.resourceURL}/courses/${courseId}/course-competencies${filtered ? '?filter=true' : ''}`, { observe: 'response' })
            .pipe(
                map((res: EntityArrayResponseType) => this.convertArrayResponseDatesFromServer(res)),
                tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))),
            );
    }

    getProgress(competencyId: number, courseId: number, refresh = false) {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.httpClient.get<CompetencyProgress>(`${this.resourceURL}/courses/${courseId}/course-competencies/${competencyId}/student-progress`, {
            params,
            observe: 'response',
        });
    }

    getCourseProgress(competencyId: number, courseId: number) {
        return this.httpClient.get<CourseCompetencyProgress>(`${this.resourceURL}/courses/${courseId}/course-competencies/${competencyId}/course-progress`, {
            observe: 'response',
        });
    }

    findById(competencyId: number, courseId: number) {
        return this.httpClient.get<Competency>(`${this.resourceURL}/courses/${courseId}/course-competencies/${competencyId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertCompetencyResponseFromServer(res);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    // triggers the generation of competencies from the given course description
    // the generated competencies are returned asynchronously over the websocket on the topic /topic/iris/competencies/{courseId}
    generateCompetenciesFromCourseDescription(courseId: number, courseDescription: string, currentCompetencies: CompetencyRecommendation[]): Observable<HttpResponse<void>> {
        const params = {
            courseDescription: courseDescription,
            currentCompetencies: currentCompetencies,
        };
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/course-competencies/generate-from-description`, params, {
            observe: 'response',
        });
    }

    getCourseCompetencyTitles(courseId: number) {
        return this.httpClient.get<string[]>(`${this.resourceURL}/courses/${courseId}/course-competencies/titles`, {
            observe: 'response',
        });
    }

    importAll(courseId: number, sourceCourseId: number, importRelations: boolean) {
        const params = new HttpParams().set('importRelations', importRelations);
        return this.httpClient.post<Array<CompetencyWithTailRelationDTO>>(`${this.resourceURL}/courses/${courseId}/course-competencies/import-all/${sourceCourseId}`, null, {
            params: params,
            observe: 'response',
        });
    }

    createCompetencyRelation(relation: CompetencyRelation, courseId: number) {
        const relationDTO: CompetencyRelationDTO = { tailCompetencyId: relation.tailCompetency?.id, headCompetencyId: relation.headCompetency?.id, relationType: relation.type };
        return this.httpClient.post<CompetencyRelationDTO>(`${this.resourceURL}/courses/${courseId}/course-competencies/relations`, relationDTO, {
            observe: 'response',
        });
    }

    getCompetencyRelations(courseId: number) {
        return this.httpClient.get<CompetencyRelationDTO[]>(`${this.resourceURL}/courses/${courseId}/course-competencies/relations`, {
            observe: 'response',
        });
    }

    removeCompetencyRelation(competencyRelationId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/course-competencies/relations/${competencyRelationId}`, {
            observe: 'response',
        });
    }

    /**
     * Retrieves the judgement of learning (JoL) value for a specific competency in a course.
     *
     * @param courseId the id of the course for which the competency belongs
     * @param competencyId the id of the competency for which to get the JoL value
     * @return an Observable of HttpResponse containing the current (and prior) JoL value or null if not set
     */
    getJoL(courseId: number, competencyId: number): Observable<CompetencyJolResponseType> {
        return this.httpClient
            .get<{ current: CompetencyJol; prior?: CompetencyJol }>(`${this.resourceURL}/courses/${courseId}/course-competencies/${competencyId}/jol`, { observe: 'response' })
            .pipe(map((res: CompetencyJolResponseType) => res));
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
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/course-competencies/${competencyId}/jol/${jolValue}`, null, { observe: 'response' });
    }

    /**
     * Retrieves the judgement of learning (JoL) values for all competencies of a specified course.
     *
     * @param courseId the id of the course for which the JoL values should be fetched
     * @return an Observable of HttpResponse containing a map from competency id to current (and prior) JoL value
     */
    getJoLAllForCourse(courseId: number): Observable<CompetencyJolMapResponseType> {
        return this.httpClient
            .get<{ [key: number]: { current: CompetencyJol; prior?: CompetencyJol } }>(`${this.resourceURL}/courses/${courseId}/course-competencies/jol`, { observe: 'response' })
            .pipe(map((res: CompetencyJolMapResponseType) => res));
    }

    protected convertCompetencyResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.softDueDate) {
            res.body.softDueDate = convertDateFromServer(res.body.softDueDate);
        }
        res.body?.lectureUnitLinks?.forEach((lectureUnitLink) => {
            if (lectureUnitLink.lectureUnit) {
                lectureUnitLink.lectureUnit = this.lectureUnitService.convertLectureUnitDateFromServer(lectureUnitLink.lectureUnit);
            }
        });
        if (res.body?.course) {
            this.accountService.setAccessRightsForCourse(res.body.course);
        }
        this.convertExerciseLinksFromServer(res.body?.exerciseLinks);

        return res;
    }

    private convertExerciseLinksFromServer(exerciseLinks: CompetencyExerciseLink[] | undefined) {
        exerciseLinks?.forEach((exerciseLink) => {
            exerciseLink.exercise = ExerciseService.convertExerciseDatesFromServer(exerciseLink.exercise);
            ExerciseService.parseExerciseCategories(exerciseLink.exercise);
            if (exerciseLink.exercise) {
                this.accountService.setAccessRightsForExercise(exerciseLink.exercise);
            }
        });
    }

    protected convertCompetencyFromClient(courseCompetency: CourseCompetency): CourseCompetency {
        const copy = Object.assign({}, courseCompetency, {
            softDueDate: convertDateFromClient(courseCompetency.softDueDate),
        });

        this.convertCompetencyLinksFromClient(copy);

        return copy;
    }

    private convertCompetencyLinksFromClient(courseCompetency: CourseCompetency) {
        courseCompetency.lectureUnitLinks?.forEach((lectureUnitLink) => {
            if (lectureUnitLink.lectureUnit) {
                lectureUnitLink.lectureUnit = this.lectureUnitService.convertLectureUnitDatesFromClient(lectureUnitLink.lectureUnit);
            }
        });
        courseCompetency.exerciseLinks?.forEach((exerciseLink) => {
            if (exerciseLink.exercise) {
                exerciseLink.exercise = ExerciseService.convertExerciseFromClient(exerciseLink.exercise);
            }
        });
    }

    /**
     * Helper methods for date conversion from server and client
     */
    protected convertArrayResponseDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.map((competency: CourseCompetency) => (competency.softDueDate = convertDateFromServer(competency.softDueDate)));
        }
        return res;
    }

    protected sendTitlesToEntityTitleService(competency: CourseCompetency | undefined | null) {
        this.entityTitleService.setTitle(EntityType.COMPETENCY, [competency?.id], competency?.title);
    }
}

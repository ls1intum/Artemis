import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Competency, CompetencyProgress, CompetencyRelation, CourseCompetencyProgress } from 'app/entities/competency.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map, tap } from 'rxjs/operators';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';

type EntityResponseType = HttpResponse<Competency>;
type EntityArrayResponseType = HttpResponse<Competency[]>;

@Injectable({
    providedIn: 'root',
})
export class CompetencyService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient, private entityTitleService: EntityTitleService, private lectureUnitService: LectureUnitService) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<Competency[]>(`${this.resourceURL}/courses/${courseId}/competencies`, { observe: 'response' })
            .pipe(tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    getAllPrerequisitesForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<Competency[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' })
            .pipe(tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
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

    create(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    import(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const competencyCopy = this.convertCompetencyFromClient(competency);
        return this.httpClient.post<Competency>(`${this.resourceURL}/courses/${courseId}/competencies/import`, competencyCopy, { observe: 'response' });
    }

    addPrerequisite(competencyId: number, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/prerequisites/${competencyId}`, null, { observe: 'response' });
    }

    update(competency: Competency, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertCompetencyFromClient(competency);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/competencies`, copy, { observe: 'response' });
    }

    delete(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}`, { observe: 'response' });
    }

    removePrerequisite(competencyId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${competencyId}`, { observe: 'response' });
    }

    createCompetencyRelation(tailCompetencyId: number, headCompetencyId: number, type: string, courseId: number): Observable<EntityResponseType> {
        let params = new HttpParams();
        params = params.set('type', type);
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/competencies/${tailCompetencyId}/relations/${headCompetencyId}`, null, {
            observe: 'response',
            params,
        });
    }

    getCompetencyRelations(competencyId: number, courseId: number) {
        return this.httpClient.get<CompetencyRelation[]>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/relations`, {
            observe: 'response',
        });
    }

    removeCompetencyRelation(competencyId: number, competencyRelationId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/relations/${competencyRelationId}`, {
            observe: 'response',
        });
    }

    convertCompetencyResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(res.body.lectureUnits);
        }
        if (res.body?.exercises) {
            res.body.exercises = ExerciseService.convertExercisesDateFromServer(res.body.exercises);
            res.body.exercises.forEach((exercise) => ExerciseService.parseExerciseCategories(exercise));
        }
        return res;
    }

    convertCompetencyFromClient(competency: Competency): Competency {
        const copy = Object.assign({}, competency);
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromClient(copy.lectureUnits);
        }
        if (copy.exercises) {
            copy.exercises = copy.exercises.map((exercise) => ExerciseService.convertExerciseFromClient(exercise));
        }
        return copy;
    }

    private sendTitlesToEntityTitleService(competency: Competency | undefined | null) {
        this.entityTitleService.setTitle(EntityType.COMPETENCY, [competency?.id], competency?.title);
    }
}

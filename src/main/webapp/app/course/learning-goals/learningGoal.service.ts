import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map, tap } from 'rxjs/operators';

import { CourseLearningGoalProgress, LearningGoal, LearningGoalProgress, LearningGoalRelation } from 'app/entities/learningGoal.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { EntityTitleService, EntityType } from 'app/shared/layouts/navbar/entity-title.service';

type EntityResponseType = HttpResponse<LearningGoal>;
type EntityArrayResponseType = HttpResponse<LearningGoal[]>;

@Injectable({
    providedIn: 'root',
})
export class LearningGoalService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private entityTitleService: EntityTitleService, private lectureUnitService: LectureUnitService) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/learning-goals`, { observe: 'response' })
            .pipe(tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    getAllPrerequisitesForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' })
            .pipe(tap((res: EntityArrayResponseType) => res?.body?.forEach(this.sendTitlesToEntityTitleService.bind(this))));
    }

    getProgress(learningGoalId: number, courseId: number, refresh = false) {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.httpClient.get<LearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}/student-progress`, {
            params,
            observe: 'response',
        });
    }

    getCourseProgress(learningGoalId: number, courseId: number) {
        return this.httpClient.get<CourseLearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}/course-progress`, {
            observe: 'response',
        });
    }

    findById(learningGoalId: number, courseId: number) {
        return this.httpClient.get<LearningGoal>(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}`, { observe: 'response' }).pipe(
            map((res: EntityResponseType) => {
                this.convertLearningGoalResponseFromServer(res);
                this.sendTitlesToEntityTitleService(res?.body);
                return res;
            }),
        );
    }

    create(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertLearningGoalFromClient(learningGoal);
        return this.httpClient.post<LearningGoal>(`${this.resourceURL}/courses/${courseId}/learning-goals`, copy, { observe: 'response' });
    }

    addPrerequisite(learningGoalId: number, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/prerequisites/${learningGoalId}`, null, { observe: 'response' });
    }

    update(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertLearningGoalFromClient(learningGoal);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/learning-goals`, copy, { observe: 'response' });
    }

    delete(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}`, { observe: 'response' });
    }

    removePrerequisite(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${learningGoalId}`, { observe: 'response' });
    }

    createLearningGoalRelation(tailLearningGoalId: number, headLearningGoalId: number, type: string, courseId: number): Observable<EntityResponseType> {
        let params = new HttpParams();
        params = params.set('type', type);
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/learning-goals/${tailLearningGoalId}/relations/${headLearningGoalId}`, null, {
            observe: 'response',
            params,
        });
    }

    getLearningGoalRelations(learningGoalId: number, courseId: number) {
        return this.httpClient.get<LearningGoalRelation[]>(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}/relations`, {
            observe: 'response',
        });
    }

    removeLearningGoalRelation(learningGoalId: number, learningGoalRelationId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/learning-goals/${learningGoalId}/relations/${learningGoalRelationId}`, {
            observe: 'response',
        });
    }

    convertLearningGoalResponseFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(res.body.lectureUnits);
        }
        if (res.body?.exercises) {
            res.body.exercises = ExerciseService.convertExercisesDateFromServer(res.body.exercises);
            res.body.exercises.forEach((exercise) => ExerciseService.parseExerciseCategories(exercise));
        }
        return res;
    }

    convertLearningGoalFromClient(learningGoal: LearningGoal): LearningGoal {
        const copy = Object.assign({}, learningGoal);
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromClient(copy.lectureUnits);
        }
        if (copy.exercises) {
            copy.exercises = copy.exercises.map((exercise) => ExerciseService.convertExerciseFromClient(exercise));
        }
        return copy;
    }

    private sendTitlesToEntityTitleService(learningGoal: LearningGoal | undefined | null) {
        this.entityTitleService.setTitle(EntityType.LEARNING_GOAL, [learningGoal?.id], learningGoal?.title);
    }
}

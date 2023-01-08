import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CourseLearningGoalProgress, LearningGoal, LearningGoalProgress, LearningGoalRelation } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<LearningGoal>;
type EntityArrayResponseType = HttpResponse<LearningGoal[]>;

@Injectable({
    providedIn: 'root',
})
export class LearningGoalService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient, private lectureUnitService: LectureUnitService) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/goals`, { observe: 'response' });
    }

    getAllPrerequisitesForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/prerequisites`, { observe: 'response' });
    }

    getProgress(learningGoalId: number, courseId: number, refresh = false) {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.httpClient.get<LearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/student-progress`, {
            params,
            observe: 'response',
        });
    }

    getCourseProgress(learningGoalId: number, courseId: number) {
        return this.httpClient.get<CourseLearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/course-progress`, {
            observe: 'response',
        });
    }

    findById(learningGoalId: number, courseId: number) {
        return this.httpClient
            .get<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertLectureUnitArrayResponseDateFromServer(res)));
    }

    create(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertLearningGoalDatesFromClient(learningGoal);
        return this.httpClient.post<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    addPrerequisite(learningGoalId: number, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/prerequisites/${learningGoalId}`, null, { observe: 'response' });
    }

    update(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertLearningGoalDatesFromClient(learningGoal);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    delete(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' });
    }

    removePrerequisite(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/prerequisites/${learningGoalId}`, { observe: 'response' });
    }

    createLearningGoalRelation(tailLearningGoalId: number, headLearningGoalId: number, type: string, courseId: number): Observable<EntityResponseType> {
        let params = new HttpParams();
        params = params.set('type', type);
        return this.httpClient.post(`${this.resourceURL}/courses/${courseId}/goals/${tailLearningGoalId}/relations/${headLearningGoalId}`, null, {
            observe: 'response',
            params,
        });
    }

    getLearningGoalRelations(learningGoalId: number, courseId: number) {
        return this.httpClient.get<LearningGoalRelation[]>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/relations`, {
            observe: 'response',
        });
    }

    removeLearningGoalRelation(learningGoalId: number, learningGoalRelationId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/relations/${learningGoalRelationId}`, {
            observe: 'response',
        });
    }

    convertLectureUnitArrayResponseDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromServer(res.body.lectureUnits);
        }
        return res;
    }

    convertLearningGoalDatesFromClient(learningGoal: LearningGoal): LearningGoal {
        const copy = Object.assign({}, learningGoal);
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertLectureUnitArrayDatesFromClient(copy.lectureUnits);
        }
        return copy;
    }
}

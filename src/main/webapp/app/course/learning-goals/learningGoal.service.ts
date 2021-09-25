import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';
import { LectureUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/lectureUnit.service';
import { map } from 'rxjs/operators';
import { IndividualLearningGoalProgress } from 'app/course/learning-goals/learning-goal-individual-progress-dtos.model';
import { CourseLearningGoalProgress } from 'app/course/learning-goals/learning-goal-course-progress.dtos.model';

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

    getProgress(learningGoalId: number, courseId: number, useParticipantScoreTable = false) {
        let params = new HttpParams();
        params = params.set('useParticipantScoreTable', String(useParticipantScoreTable));
        return this.httpClient.get<IndividualLearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/individual-progress`, {
            observe: 'response',
            params,
        });
    }

    getCourseProgress(learningGoalId: number, courseId: number, useParticipantScoreTable = false) {
        let params = new HttpParams();
        params = params.set('useParticipantScoreTable', String(useParticipantScoreTable));
        return this.httpClient.get<CourseLearningGoalProgress>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}/course-progress`, {
            observe: 'response',
            params,
        });
    }

    findById(learningGoalId: number, courseId: number) {
        return this.httpClient
            .get<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServerResponse(res)));
    }

    create(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(learningGoal);
        return this.httpClient.post<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    update(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(learningGoal);
        return this.httpClient.put(`${this.resourceURL}/courses/${courseId}/goals`, copy, { observe: 'response' });
    }

    delete(learningGoalId: number, courseId: number) {
        return this.httpClient.delete(`${this.resourceURL}/courses/${courseId}/goals/${learningGoalId}`, { observe: 'response' });
    }

    convertDateFromServerResponse(res: EntityResponseType): EntityResponseType {
        if (res.body?.lectureUnits) {
            res.body.lectureUnits = this.lectureUnitService.convertDateArrayFromServerEntity(res.body.lectureUnits);
        }
        return res;
    }

    convertDateFromClient(learningGoal: LearningGoal): LearningGoal {
        const copy = Object.assign({}, learningGoal);
        if (copy.lectureUnits) {
            copy.lectureUnits = this.lectureUnitService.convertDateArrayFromClient(copy.lectureUnits);
        }
        return copy;
    }
}

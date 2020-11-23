import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LearningGoal } from 'app/entities/learningGoal.model';

type EntityResponseType = HttpResponse<LearningGoal>;
type EntityArrayResponseType = HttpResponse<LearningGoal[]>;

@Injectable({
    providedIn: 'root',
})
export class LearningGoalService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<LearningGoal[]>(`${this.resourceURL}/courses/${courseId}/goals`, { observe: 'response' });
    }

    create(learningGoal: LearningGoal, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<LearningGoal>(`${this.resourceURL}/courses/${courseId}/goals`, learningGoal, { observe: 'response' });
    }
}

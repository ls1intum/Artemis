import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable } from 'rxjs';
import { LearningGoal } from 'app/entities/learning-goal.model';

type EntityResponseType = HttpResponse<LearningGoal>;
type EntityArrayResponseType = HttpResponse<LearningGoal[]>;

@Injectable({ providedIn: 'root' })
export class LearningGoalManagementService {
    private resourceUrl = SERVER_API_URL + 'api/learning-goals';

    constructor(private httpClient: HttpClient) {}

    /**
     * Finds all the learning goals associated with a course
     * @param courseId - the id of the course for which the learning goals should be found
     */
    findAllByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<LearningGoal[]>(`api/courses/${courseId}/goals`, { observe: 'response' });
    }

    /**
     * Create a learning goal
     * @param learningGoal - the learning goal to create
     */
    createLearningGoal(learningGoal: LearningGoal): Observable<EntityResponseType> {
        return this.httpClient.post<LearningGoal>(`api/goals`, learningGoal, { observe: 'response' });
    }

    /**
     * Update a learning goal
     * @param learningGoal - the learning goal to update
     */
    updateLearningGoal(learningGoal: LearningGoal): Observable<EntityResponseType> {
        return this.httpClient.put<LearningGoal>(`api/goals`, learningGoal, { observe: 'response' });
    }

    findById(learningGoalId: number): Observable<EntityResponseType> {
        return this.httpClient.get<LearningGoal>(`api/goals/${learningGoalId}`, { observe: 'response' });
    }
}

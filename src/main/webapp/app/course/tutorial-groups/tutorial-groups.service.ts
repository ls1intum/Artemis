import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group.model';
import { Observable } from 'rxjs';

type EntityResponseType = HttpResponse<TutorialGroup>;
type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' });
    }

    create(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, tutorialGroup, { observe: 'response' });
    }

    findById(tutorialGroupId: number, courseId: number) {
        return this.httpClient.get<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }

    update(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.put<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, tutorialGroup, { observe: 'response' });
    }
}

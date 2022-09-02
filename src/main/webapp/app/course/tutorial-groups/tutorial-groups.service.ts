import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Observable } from 'rxjs';
import { StudentDTO } from 'app/entities/student-dto.model';

type EntityResponseType = HttpResponse<TutorialGroup>;
type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getAllOfCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' });
    }

    getOne(tutorialGroupId: number) {
        return this.httpClient.get<TutorialGroup>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }

    create(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, tutorialGroup, { observe: 'response' });
    }

    update(tutorialGroup: TutorialGroup): Observable<EntityResponseType> {
        return this.httpClient.put<TutorialGroup>(`${this.resourceURL}/tutorial-groups`, tutorialGroup, { observe: 'response' });
    }

    deregisterStudent(tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}/deregister/${login}`, { observe: 'response' });
    }

    registerStudent(tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}/register/${login}`, {}, { observe: 'response' });
    }

    registerMultipleStudents(tutorialGroupId: number, studentDtos: StudentDTO[]): Observable<HttpResponse<StudentDTO[]>> {
        return this.httpClient.post<StudentDTO[]>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}/register-multiple`, studentDtos, {
            observe: 'response',
        });
    }

    delete(tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }
}

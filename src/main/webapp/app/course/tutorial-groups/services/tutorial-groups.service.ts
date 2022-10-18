import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Observable } from 'rxjs';
import { StudentDTO } from 'app/entities/student-dto.model';
import { TutorialGroupRegistrationImportDTO } from 'app/entities/tutorial-group/tutorial-group-import-dto.model';

type EntityResponseType = HttpResponse<TutorialGroup>;
type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getUniqueCampusValues(courseId: number): Observable<HttpResponse<string[]>> {
        return this.httpClient.get<string[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/campus-values`, { observe: 'response' });
    }

    getAllOfCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient.get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' });
    }

    getOneOfCourse(courseId: number, tutorialGroupId: number) {
        return this.httpClient.get<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }

    create(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        return this.httpClient.post<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, tutorialGroup, { observe: 'response' });
    }

    update(courseId: number, tutorialGroupId: number, tutorialGroup: TutorialGroup): Observable<EntityResponseType> {
        return this.httpClient.put<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, tutorialGroup, { observe: 'response' });
    }

    deregisterStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/deregister/${login}`, { observe: 'response' });
    }

    registerStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/register/${login}`, {}, { observe: 'response' });
    }

    registerMultipleStudents(courseId: number, tutorialGroupId: number, studentDtos: StudentDTO[]): Observable<HttpResponse<StudentDTO[]>> {
        return this.httpClient.post<StudentDTO[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/register-multiple`, studentDtos, {
            observe: 'response',
        });
    }

    import(courseId: number, tutorialGroups: TutorialGroupRegistrationImportDTO[]): Observable<HttpResponse<TutorialGroupRegistrationImportDTO[]>> {
        return this.httpClient.post<TutorialGroupRegistrationImportDTO[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/import`, tutorialGroups, {
            observe: 'response',
        });
    }

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }
}

import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { Observable } from 'rxjs';
import { StudentDTO } from 'app/entities/student-dto.model';
import { convertDateFromServer } from 'app/utils/date.utils';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<TutorialGroup>;
type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private resourceURL = SERVER_API_URL + 'api';

    constructor(private httpClient: HttpClient) {}

    getAllOfCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertTutorialGroupResponseArrayDatesFromServer(res)));
    }

    getOneOfCourse(tutorialGroupId: number, courseId: number) {
        return this.httpClient
            .get<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
    }

    create(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupDatesFromClient(tutorialGroup);
        return this.httpClient
            .post<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
    }

    update(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupDatesFromClient(tutorialGroup);
        return this.httpClient
            .put<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
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

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.httpClient.delete<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' });
    }

    private convertTutorialGroupDatesFromServer(tutorialGroup: TutorialGroup): TutorialGroup {
        if (tutorialGroup.tutorialGroupSchedule) {
            tutorialGroup.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validFromInclusive);
            tutorialGroup.tutorialGroupSchedule.validToInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validToInclusive);
        }
        return tutorialGroup;
    }

    private convertTutorialGroupResponseDatesFromServer(res: HttpResponse<TutorialGroup>): HttpResponse<TutorialGroup> {
        if (res.body?.tutorialGroupSchedule) {
            res.body.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(res.body.tutorialGroupSchedule.validFromInclusive);
            res.body.tutorialGroupSchedule.validToInclusive = convertDateFromServer(res.body.tutorialGroupSchedule.validToInclusive);
        }
        return res;
    }

    private convertTutorialGroupResponseArrayDatesFromServer(res: HttpResponse<TutorialGroup[]>): HttpResponse<TutorialGroup[]> {
        if (res.body) {
            res.body.forEach((tutorialGroup: TutorialGroup) => {
                this.convertTutorialGroupDatesFromServer(tutorialGroup);
            });
        }
        return res;
    }

    private convertTutorialGroupDatesFromClient(tutorialGroup: TutorialGroup): TutorialGroup {
        if (tutorialGroup.tutorialGroupSchedule) {
            return Object.assign({}, tutorialGroup, {
                tutorialGroupSchedule: Object.assign({}, tutorialGroup.tutorialGroupSchedule, {
                    validFromInclusive: tutorialGroup.tutorialGroupSchedule.validFromInclusive!.format('YYYY-MM-DD'),
                    validToInclusive: tutorialGroup.tutorialGroupSchedule.validToInclusive!.format('YYYY-MM-DD'),
                }),
            });
        } else {
            return tutorialGroup;
        }
    }
}

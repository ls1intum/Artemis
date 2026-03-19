import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import {
    CreateOrUpdateTutorialGroupDTO,
    TutorialGroup,
    TutorialGroupRegisterStudentDTO,
    TutorialGroupRegisteredStudentDTO,
    TutorialGroupScheduleDTO,
} from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Student } from 'app/openapi/model/student';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupExport } from 'app/openapi/model/tutorialGroupExport';
import { HttpParams } from '@angular/common/http';
import { TutorialGroupImport } from 'app/openapi/model/tutorialGroupImport';
import { convertTutorialGroupResponseArrayDatesFromServer } from 'app/tutorialgroup/shared/util/convertTutorialGroupEntityDates';

type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private httpClient = inject(HttpClient);
    private tutorialGroupApiService = inject(TutorialGroupApiService);

    private resourceURL = 'api/tutorialgroup';

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => convertTutorialGroupResponseArrayDatesFromServer(res)));
    }

    create(courseId: number, createTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO): Observable<void> {
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, createTutorialGroupDTO);
    }

    update(courseId: number, tutorialGroupId: number, updateTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO): Observable<void> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, updateTutorialGroupDTO);
    }

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.deleteTutorialGroup(courseId, tutorialGroupId, 'response');
    }

    getTutorialGroupScheduleDTO(courseId: number, tutorialGroupId: number): Observable<TutorialGroupScheduleDTO | undefined> {
        return this.httpClient
            .get<TutorialGroupScheduleDTO | null>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/schedule`)
            .pipe(map((dto) => dto ?? undefined));
    }

    getRegisteredStudentDTOs(courseId: number, tutorialGroupId: number): Observable<TutorialGroupRegisteredStudentDTO[]> {
        return this.httpClient.get<TutorialGroupRegisteredStudentDTO[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/registered-students`);
    }

    getUnregisteredStudentDTOs(
        courseId: number,
        tutorialGroupId: number,
        loginOrName: string,
        pageIndex: number,
        pageSize: number,
    ): Observable<TutorialGroupRegisteredStudentDTO[]> {
        const params = new HttpParams().set('loginOrName', loginOrName).set('pageIndex', pageIndex).set('pageSize', pageSize);
        return this.httpClient.get<TutorialGroupRegisteredStudentDTO[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/unregistered-students`, {
            params,
        });
    }

    deregisterStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.deregisterStudent(courseId, tutorialGroupId, login, 'response');
    }

    importRegistrations(courseId: number, tutorialGroupId: number, studentDtos: Student[]): Observable<HttpResponse<Array<TutorialGroupRegisterStudentDTO>>> {
        return this.httpClient.post<Array<Student>>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/import-registrations`, studentDtos, {
            observe: 'response',
        });
    }

    registerMultipleStudentsViaLogin(courseId: number, tutorialGroupId: number, logins: string[]): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/register-via-login`, logins, { observe: 'response' });
    }

    import(courseId: number, tutorialGroups: TutorialGroupImport[]): Observable<HttpResponse<Array<TutorialGroupImport>>> {
        return this.tutorialGroupApiService.importTutorialGroupsWithRegistrations(courseId, tutorialGroups, 'response');
    }

    exportTutorialGroupsToCSV(courseId: number, fields: string[]): Observable<Blob> {
        return this.tutorialGroupApiService.exportTutorialGroupsToCSV(courseId, fields);
    }

    exportToJson(courseId: number, fields: string[]): Observable<string> {
        return this.tutorialGroupApiService.exportTutorialGroupsToJSON(courseId, fields).pipe(
            map((data: Array<TutorialGroupExport>) => {
                return JSON.stringify(data);
            }),
        );
    }
}

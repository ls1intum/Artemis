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
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { Student } from 'app/openapi/model/student';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupRegistrationImport } from 'app/openapi/model/tutorialGroupRegistrationImport';
import { TutorialGroupExport } from 'app/openapi/model/tutorialGroupExport';
import { HttpParams } from '@angular/common/http';

type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private httpClient = inject(HttpClient);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);

    private resourceURL = 'api/tutorialgroup';

    getUniqueLanguageValues(courseId: number): Observable<HttpResponse<Array<string>>> {
        return this.tutorialGroupApiService.getUniqueLanguageValues(courseId, 'response');
    }

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertTutorialGroupResponseArrayDatesFromServer(res)));
    }

    create(courseId: number, createTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO): Observable<void> {
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, createTutorialGroupDTO);
    }

    update(courseId: number, tutorialGroupId: number, updateTutorialGroupDTO: CreateOrUpdateTutorialGroupDTO): Observable<void> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, updateTutorialGroupDTO);
    }

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.delete(courseId, tutorialGroupId, 'response');
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

    registerMultipleStudentsViaLoginOrRegistrationNumber(
        courseId: number,
        tutorialGroupId: number,
        studentDtos: Student[],
    ): Observable<HttpResponse<Array<TutorialGroupRegisterStudentDTO>>> {
        return this.httpClient.post<Array<Student>>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/register-multiple`, studentDtos, {
            observe: 'response',
        });
    }

    registerMultipleStudentsViaLogin(courseId: number, tutorialGroupId: number, logins: string[]): Observable<HttpResponse<void>> {
        return this.httpClient.post<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/register-via-login`, logins, { observe: 'response' });
    }

    import(courseId: number, tutorialGroups: TutorialGroupRegistrationImport[]): Observable<HttpResponse<Array<TutorialGroupRegistrationImport>>> {
        return this.tutorialGroupApiService.importRegistrations(courseId, tutorialGroups, 'response');
    }

    convertTutorialGroupArrayDatesFromServer(tutorialGroups: TutorialGroup[]): TutorialGroup[] {
        if (tutorialGroups) {
            tutorialGroups.forEach((tutorialGroup: TutorialGroup) => {
                this.convertTutorialGroupDatesFromServer(tutorialGroup);
            });
        }
        return tutorialGroups;
    }

    convertTutorialGroupDatesFromServer(tutorialGroup: TutorialGroup): TutorialGroup {
        if (tutorialGroup.tutorialGroupSchedule) {
            tutorialGroup.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validFromInclusive);
            tutorialGroup.tutorialGroupSchedule.validToInclusive = convertDateFromServer(tutorialGroup.tutorialGroupSchedule.validToInclusive);
        }
        if (tutorialGroup.tutorialGroupSessions) {
            tutorialGroup.tutorialGroupSessions.map((tutorialGroupSession: TutorialGroupSession) =>
                this.tutorialGroupSessionService.convertTutorialGroupSessionDatesFromServer(tutorialGroupSession),
            );
        }

        if (tutorialGroup.nextSession) {
            tutorialGroup.nextSession = this.tutorialGroupSessionService.convertTutorialGroupSessionDatesFromServer(tutorialGroup.nextSession);
        }

        if (tutorialGroup.course?.tutorialGroupsConfiguration) {
            tutorialGroup.course.tutorialGroupsConfiguration = this.tutorialGroupsConfigurationService.convertTutorialGroupsConfigurationDatesFromServer(
                tutorialGroup.course?.tutorialGroupsConfiguration,
            );
        }

        return tutorialGroup;
    }

    convertTutorialGroupResponseArrayDatesFromServer(res: HttpResponse<TutorialGroup[]>): HttpResponse<TutorialGroup[]> {
        if (res.body) {
            res.body.forEach((tutorialGroup: TutorialGroup) => {
                this.convertTutorialGroupDatesFromServer(tutorialGroup);
            });
        }
        return res;
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

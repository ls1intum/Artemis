import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { TutorialGroup, TutorialGroupRegisterStudentDTO, TutorialGroupRegisteredStudentDTO, UpdateTutorialGroupDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
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

type EntityResponseType = HttpResponse<TutorialGroup>;
type EntityArrayResponseType = HttpResponse<TutorialGroup[]>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private httpClient = inject(HttpClient);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private tutorialGroupApiService = inject(TutorialGroupApiService);

    private resourceURL = 'api/tutorialgroup';

    getUniqueCampusValues(courseId: number): Observable<HttpResponse<Array<string>>> {
        return this.tutorialGroupApiService.getUniqueCampusValues(courseId, 'response');
    }

    getUniqueLanguageValues(courseId: number): Observable<HttpResponse<Array<string>>> {
        return this.tutorialGroupApiService.getUniqueLanguageValues(courseId, 'response');
    }

    getAllForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.httpClient
            .get<TutorialGroup[]>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertTutorialGroupResponseArrayDatesFromServer(res)));
    }

    getOneOfCourse(courseId: number, tutorialGroupId: number) {
        return this.httpClient
            .get<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
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

    moveStudentToOtherGroup(courseId: number, tutorialGroupId: number, studentId: number, otherTutorialGroupId: number): Observable<void> {
        return this.httpClient.patch<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}/registered-students/${studentId}`, null, {
            params: {
                otherTutorialGroupId: otherTutorialGroupId,
            },
        });
    }

    create(tutorialGroup: TutorialGroup, courseId: number): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupDatesFromClient(tutorialGroup);
        return this.httpClient
            .post<TutorialGroup>(`${this.resourceURL}/courses/${courseId}/tutorial-groups`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
    }

    update(
        courseId: number,
        tutorialGroupId: number,
        tutorialGroup: TutorialGroup,
        notificationText?: string,
        updateTutorialGroupChannelName?: boolean,
    ): Observable<EntityResponseType> {
        const copy = this.convertTutorialGroupDatesFromClient(tutorialGroup);
        return this.httpClient
            .put<TutorialGroup>(
                `${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`,
                {
                    tutorialGroup: copy,
                    notificationText,
                    updateTutorialGroupChannelName: updateTutorialGroupChannelName ?? false,
                },
                { observe: 'response' },
            )
            .pipe(map((res: EntityResponseType) => this.convertTutorialGroupResponseDatesFromServer(res)));
    }

    update2(courseId: number, tutorialGroupId: number, updateTutorialGroupDTO: UpdateTutorialGroupDTO): Observable<void> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/tutorial-groups/${tutorialGroupId}`, updateTutorialGroupDTO);
    }

    deregisterStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.deregisterStudent(courseId, tutorialGroupId, login, 'response');
    }

    registerStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.registerStudent(courseId, tutorialGroupId, login, 'response');
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

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupApiService.delete(courseId, tutorialGroupId, 'response');
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

    convertTutorialGroupResponseDatesFromServer(res: HttpResponse<TutorialGroup>): HttpResponse<TutorialGroup> {
        if (res.body?.tutorialGroupSchedule) {
            res.body.tutorialGroupSchedule.validFromInclusive = convertDateFromServer(res.body.tutorialGroupSchedule.validFromInclusive);
            res.body.tutorialGroupSchedule.validToInclusive = convertDateFromServer(res.body.tutorialGroupSchedule.validToInclusive);
        }
        if (res.body?.tutorialGroupSessions) {
            res.body.tutorialGroupSessions.map((tutorialGroupSession: TutorialGroupSession) =>
                this.tutorialGroupSessionService.convertTutorialGroupSessionDatesFromServer(tutorialGroupSession),
            );
        }
        if (res.body?.nextSession) {
            res.body.nextSession = this.tutorialGroupSessionService.convertTutorialGroupSessionDatesFromServer(res?.body.nextSession);
        }
        if (res.body?.course?.tutorialGroupsConfiguration) {
            res.body.course.tutorialGroupsConfiguration = this.tutorialGroupsConfigurationService.convertTutorialGroupsConfigurationDatesFromServer(
                res.body?.course?.tutorialGroupsConfiguration,
            );
        }
        return res;
    }

    convertTutorialGroupResponseArrayDatesFromServer(res: HttpResponse<TutorialGroup[]>): HttpResponse<TutorialGroup[]> {
        if (res.body) {
            res.body.forEach((tutorialGroup: TutorialGroup) => {
                this.convertTutorialGroupDatesFromServer(tutorialGroup);
            });
        }
        return res;
    }

    convertTutorialGroupDatesFromClient(tutorialGroup: TutorialGroup): TutorialGroup {
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

    /**
     * Export tutorial groups for a specific course to a CSV file.
     *
     * @param courseId the id of the course for which the tutorial groups should be exported
     * @param fields   the list of fields to include in the CSV export
     * @return an Observable containing the CSV file as a Blob
     */
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

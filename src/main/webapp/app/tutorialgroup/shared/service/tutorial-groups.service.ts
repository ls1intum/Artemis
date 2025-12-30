import { HttpClient, HttpResponse, HttpResourceRef } from '@angular/common/http';
import { EnvironmentInjector, Injectable, Signal, inject, runInInjectionContext } from '@angular/core';
import { RawTutorialGroupDetailGroupDTO, TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { Observable } from 'rxjs';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { map } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupSessionService } from 'app/tutorialgroup/shared/service/tutorial-group-session.service';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { Student } from 'app/openapi/models/student';
import { TutorialGroupRegistrationImport } from 'app/openapi/models/tutorial-group-registration-import';
import { TutorialGroupApi } from 'app/openapi/api/tutorial-group-api';
import {
    exportTutorialGroupsToCSVResource,
    exportTutorialGroupsToJSONResource,
    getAllForCourseResource,
    getTutorialGroupDetailGroupDTOResource,
    getUniqueCampusValuesResource,
    getUniqueLanguageValuesResource,
} from 'app/openapi/api/tutorial-group-resources';
import { ExportTutorialGroupsToCSVParams, ExportTutorialGroupsToJSONParams } from 'app/openapi/api/tutorial-group-resources';

type EntityResponseType = HttpResponse<TutorialGroup>;

@Injectable({ providedIn: 'root' })
export class TutorialGroupsService {
    private httpClient = inject(HttpClient);
    private environmentInjector = inject(EnvironmentInjector);
    private tutorialGroupSessionService = inject(TutorialGroupSessionService);
    private tutorialGroupsConfigurationService = inject(TutorialGroupsConfigurationService);
    private tutorialGroupApi = inject(TutorialGroupApi);

    private resourceURL = 'api/tutorialgroup';

    getUniqueCampusValuesResource(courseId: Signal<number> | number): HttpResourceRef<Array<string> | undefined> {
        return this.createResource(() => getUniqueCampusValuesResource(courseId));
    }

    getUniqueLanguageValuesResource(courseId: Signal<number> | number): HttpResourceRef<Array<string> | undefined> {
        return this.createResource(() => getUniqueLanguageValuesResource(courseId));
    }

    getAllForCourseResource(courseId: Signal<number> | number): HttpResourceRef<Array<TutorialGroup> | undefined> {
        return this.createResource(() => getAllForCourseResource(courseId)) as HttpResourceRef<Array<TutorialGroup> | undefined>;
    }

    getTutorialGroupDetailGroupDTOResource(
        courseId: Signal<number> | number,
        tutorialGroupId: Signal<number> | number,
    ): HttpResourceRef<RawTutorialGroupDetailGroupDTO | undefined> {
        return this.createResource(() => getTutorialGroupDetailGroupDTOResource(courseId, tutorialGroupId)) as HttpResourceRef<RawTutorialGroupDetailGroupDTO | undefined>;
    }

    exportTutorialGroupsToCSVResource(
        courseId: Signal<number> | number,
        params?: Signal<ExportTutorialGroupsToCSVParams>,
    ): HttpResourceRef<Blob | undefined> {
        return this.createResource(() => exportTutorialGroupsToCSVResource(courseId, params));
    }

    exportTutorialGroupsToJSONResource(
        courseId: Signal<number> | number,
        params?: Signal<ExportTutorialGroupsToJSONParams>,
    ): HttpResourceRef<Array<any> | undefined> {
        return this.createResource(() => exportTutorialGroupsToJSONResource(courseId, params));
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

    deregisterStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.tutorialGroupApi.deregisterStudent(courseId, tutorialGroupId, login).pipe(map(() => new HttpResponse<void>({ status: 200 })));
    }

    registerStudent(courseId: number, tutorialGroupId: number, login: string): Observable<HttpResponse<void>> {
        return this.tutorialGroupApi.registerStudent(courseId, tutorialGroupId, login).pipe(map(() => new HttpResponse<void>({ status: 200 })));
    }

    registerMultipleStudents(courseId: number, tutorialGroupId: number, studentDtos: Student[]): Observable<HttpResponse<Array<Student>>> {
        return this.tutorialGroupApi
            .registerMultipleStudentsToTutorialGroup(courseId, tutorialGroupId, studentDtos)
            .pipe(map((body) => new HttpResponse<Array<Student>>({ body })));
    }

    import(courseId: number, tutorialGroups: TutorialGroupRegistrationImport[]): Observable<HttpResponse<Array<TutorialGroupRegistrationImport>>> {
        return this.tutorialGroupApi.importRegistrations(courseId, tutorialGroups).pipe(map((body) => new HttpResponse<Array<TutorialGroupRegistrationImport>>({ body })));
    }

    delete(courseId: number, tutorialGroupId: number): Observable<HttpResponse<void>> {
        return this.tutorialGroupApi.delete(courseId, tutorialGroupId).pipe(map(() => new HttpResponse<void>({ status: 200 })));
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

    private createResource<T>(factory: () => HttpResourceRef<T>) {
        return runInInjectionContext(this.environmentInjector, factory);
    }
}

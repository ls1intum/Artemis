import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CampusOnlineCourseDTO {
    campusOnlineCourseId: string;
    title: string;
    semester?: string;
    language?: string;
    responsibleInstructor?: string;
    department?: string;
    studyProgram?: string;
    alreadyImported: boolean;
}

export interface CampusOnlineLinkRequest {
    campusOnlineCourseId: string;
    responsibleInstructor?: string;
    department?: string;
    studyProgram?: string;
}

export interface CampusOnlineSyncResultDTO {
    coursesSynced: number;
    coursesFailed: number;
    usersAdded: number;
    usersNotFound: number;
}

export interface CampusOnlineOrgUnit {
    id?: number;
    externalId: string;
    name: string;
}

export interface CampusOnlineOrgUnitImportDTO {
    externalId: string;
    name: string;
}

@Injectable({ providedIn: 'root' })
export class CampusOnlineService {
    private http = inject(HttpClient);
    private resourceUrl = 'api/core/admin/campus-online';

    getOrgUnits(): Observable<CampusOnlineOrgUnit[]> {
        return this.http.get<CampusOnlineOrgUnit[]>(`${this.resourceUrl}/org-units`);
    }

    getOrgUnit(orgUnitId: number): Observable<CampusOnlineOrgUnit> {
        return this.http.get<CampusOnlineOrgUnit>(`${this.resourceUrl}/org-units/${orgUnitId}`);
    }

    createOrgUnit(orgUnit: CampusOnlineOrgUnit): Observable<HttpResponse<CampusOnlineOrgUnit>> {
        return this.http.post<CampusOnlineOrgUnit>(`${this.resourceUrl}/org-units`, orgUnit, { observe: 'response' });
    }

    updateOrgUnit(orgUnit: CampusOnlineOrgUnit & { id: number }): Observable<HttpResponse<CampusOnlineOrgUnit>> {
        return this.http.put<CampusOnlineOrgUnit>(`${this.resourceUrl}/org-units/${orgUnit.id}`, orgUnit, { observe: 'response' });
    }

    deleteOrgUnit(orgUnitId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/org-units/${orgUnitId}`, { observe: 'response' });
    }

    importOrgUnits(orgUnits: CampusOnlineOrgUnitImportDTO[]): Observable<CampusOnlineOrgUnit[]> {
        return this.http.post<CampusOnlineOrgUnit[]>(`${this.resourceUrl}/org-units/import`, orgUnits);
    }

    searchCourses(query: string, orgUnitId: string, semester?: string): Observable<CampusOnlineCourseDTO[]> {
        let params = new HttpParams().set('query', query).set('orgUnitId', orgUnitId);
        if (semester) {
            params = params.set('semester', semester);
        }
        return this.http.get<CampusOnlineCourseDTO[]>(`${this.resourceUrl}/courses/search`, { params });
    }

    linkCourse(courseId: number, request: CampusOnlineLinkRequest): Observable<HttpResponse<CampusOnlineCourseDTO>> {
        return this.http.put<CampusOnlineCourseDTO>(`${this.resourceUrl}/courses/${courseId}/link`, request, { observe: 'response' });
    }

    unlinkCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}/courses/${courseId}/link`, { observe: 'response' });
    }

    syncCourse(courseId: number): Observable<CampusOnlineSyncResultDTO> {
        return this.http.post<CampusOnlineSyncResultDTO>(`${this.resourceUrl}/courses/${courseId}/sync`, null);
    }

    syncAllCourses(): Observable<CampusOnlineSyncResultDTO> {
        return this.http.post<CampusOnlineSyncResultDTO>(`${this.resourceUrl}/sync`, null);
    }
}

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminScienceService } from 'app/admin/science/admin-science.service';
import { ScienceEnabledCourse, ScienceResearchExportAudit } from 'app/admin/science/admin-science.model';
import { ScienceEventType } from 'app/foundation/science/science.model';

describe('AdminScienceService', () => {
    let service: AdminScienceService;
    let httpMock: HttpTestingController;

    const enabledCourse: ScienceEnabledCourse = { courseId: 1, courseTitle: 'Course 1', courseShortName: 'C1', active: true };

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), AdminScienceService] });
        service = TestBed.inject(AdminScienceService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => httpMock.verify());

    it('should load the courses an administrator can enable', () => {
        const selectableCourse = { id: 3, title: 'Course 3', shortName: 'C3', semester: 'WS26' };
        service.getSelectableCourses().subscribe((courses) => expect(courses).toEqual([selectableCourse]));

        const request = httpMock.expectOne('api/atlas/admin/science/selectable-courses');
        expect(request.request.method).toBe('GET');
        request.flush([selectableCourse]);
    });

    it('should load the science-enabled course history', () => {
        service.getCourses().subscribe((courses) => expect(courses).toEqual([enabledCourse]));

        const request = httpMock.expectOne('api/atlas/admin/science/courses');
        expect(request.request.method).toBe('GET');
        request.flush([enabledCourse]);
    });

    it('should enable a course', () => {
        service.enableCourse(1).subscribe((course) => expect(course).toEqual(enabledCourse));

        const request = httpMock.expectOne('api/atlas/admin/science/courses/1');
        expect(request.request.method).toBe('PUT');
        request.flush(enabledCourse);
    });

    it('should disable a course', () => {
        const disabledCourse = { ...enabledCourse, active: false };
        service.disableCourse(1).subscribe((course) => expect(course).toEqual(disabledCourse));

        const request = httpMock.expectOne('api/atlas/admin/science/courses/1');
        expect(request.request.method).toBe('DELETE');
        request.flush(disabledCourse);
    });

    it('should load the export audit history', () => {
        const audit: ScienceResearchExportAudit = { id: 7, purpose: 'Study', fileChecksum: 'abc' };
        service.getExportAudits().subscribe((audits) => expect(audits).toEqual([audit]));

        const request = httpMock.expectOne('api/atlas/admin/science/export-audits');
        expect(request.request.method).toBe('GET');
        request.flush([audit]);
    });

    it('should post the export filter and read the response as a blob', () => {
        const requestBody = { courseIds: [1, 2], purpose: 'Study', eventTypes: [ScienceEventType.LECTURE__OPEN] };
        service.createExport(requestBody).subscribe((blob) => expect(blob.size).toBeGreaterThan(0));

        const request = httpMock.expectOne('api/atlas/admin/science/exports');
        expect(request.request.method).toBe('POST');
        expect(request.request.body).toEqual(requestBody);
        expect(request.request.responseType).toBe('blob');
        request.flush(new Blob(['identity,timestamp\n']));
    });
});

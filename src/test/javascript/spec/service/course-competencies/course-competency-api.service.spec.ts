import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { CompetencyRelationDTO, CompetencyRelationType } from 'app/entities/competency.model';

describe('CourseCompetencyApiService', () => {
    let httpClient: HttpTestingController;
    let courseCompetencyApiService: CourseCompetencyApiService;

    const baseUrl = 'api';

    const courseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseCompetencyApiService, provideHttpClient(), provideHttpClientTesting()],
        });

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        httpClient = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should get course competencies by course id', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/course-competencies` });
        response.flush({});
        await methodCall;
    });

    it('should get course competency relations by course id', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetencyRelationsByCourseId(courseId);
        const response = httpClient.expectOne({ method: 'GET', url: `${baseUrl}/courses/${courseId}/course-competencies/relations` });
        response.flush({});
        await methodCall;
    });

    it('should update course competency relation', async () => {
        const relationId = 1;
        const relationType = CompetencyRelationType.EXTENDS;
        const methodCall = courseCompetencyApiService.updateCourseCompetencyRelation(courseId, relationId, relationType);
        const response = httpClient.expectOne({ method: 'PATCH', url: `${baseUrl}/courses/${courseId}/course-competencies/relations/${relationId}` });
        response.flush({});
        await methodCall;
    });

    it('should delete course competency', async () => {
        const courseCompetencyId = 1;
        const methodCall = courseCompetencyApiService.deleteCourseCompetency(courseId, courseCompetencyId);
        const response = httpClient.expectOne({ method: 'DELETE', url: `${baseUrl}/courses/${courseId}/course-competencies/${courseCompetencyId}` });
        response.flush({});
        await methodCall;
    });

    it('should create course competency relation', async () => {
        const newRelation = <CompetencyRelationDTO>{
            relationType: CompetencyRelationType.EXTENDS,
            tailCompetencyId: 1,
            headCompetencyId: 2,
        };
        const methodCall = courseCompetencyApiService.createCourseCompetencyRelation(courseId, newRelation);
        const response = httpClient.expectOne({ method: 'POST', url: `${baseUrl}/courses/${courseId}/course-competencies/relations` });
        response.flush({});
        await methodCall;
    });

    it('should delete course competency relation', async () => {
        const relationId = 1;
        const methodCall = courseCompetencyApiService.deleteCourseCompetencyRelation(courseId, relationId);
        const response = httpClient.expectOne({ method: 'DELETE', url: `${baseUrl}/courses/${courseId}/course-competencies/relations/${relationId}` });
        response.flush({});
        await methodCall;
    });

    it('should import all courseCompetencies', async () => {
        const sourceCourseId = 2;
        const importRelations = true;
        const methodCall = courseCompetencyApiService.importAll(courseId, sourceCourseId, importRelations);
        const response = httpClient.expectOne({
            method: 'POST',
            url: `${baseUrl}/courses/${courseId}/course-competencies/import-all/${sourceCourseId}?importRelations=${importRelations}`,
        });
        response.flush({});
        await methodCall;
    });
});

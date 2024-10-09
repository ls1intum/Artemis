import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';
import { CompetencyRelation, CompetencyRelationType, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';

describe('CourseCompetencyApiService', () => {
    let httpClient: HttpTestingController;
    let courseCompetencyApiService: CourseCompetencyApiService;

    const baseUrl = 'api';

    const courseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        httpClient = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpClient.verify();
    });

    it('should import all by course id', async () => {
        const courseCompetencyImportOptions = <CourseCompetencyImportOptionsDTO>{
            sourceCourseId: courseId,
            importRelations: true,
            importExercises: true,
            importLectures: true,
        };

        const methodCall = courseCompetencyApiService.importAllByCourseId(courseId, courseCompetencyImportOptions);
        const response = httpClient.expectOne({
            method: 'POST',
            url: `${getBasePath(courseId)}/import-all`,
        });
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

    it('should create course competency relation', async () => {
        const relation = <CompetencyRelation>{
            tailCompetencyId: 1,
            headCompetencyId: 2,
        };

        const methodCall = courseCompetencyApiService.createCourseCompetencyRelation(courseId, relation);
        const response = httpClient.expectOne({
            method: 'POST',
            url: `${getBasePath(courseId)}/relations`,
        });
        response.flush({});
        await methodCall;
    });

    it('should delete course competency relation', async () => {
        const relationId = 1;

        const methodCall = courseCompetencyApiService.deleteCourseCompetencyRelation(courseId, relationId);
        const response = httpClient.expectOne({
            method: 'DELETE',
            url: `${getBasePath(courseId)}/relations/${relationId}`,
        });
        response.flush({});
        await methodCall;
    });

    it('should get course competency relations', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetencyRelations(courseId);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${getBasePath(courseId)}/relations`,
        });
        response.flush([]);
        await methodCall;
    });

    it('should get course competencies by course id', async () => {
        const methodCall = courseCompetencyApiService.getCourseCompetenciesByCourseId(courseId);
        const response = httpClient.expectOne({
            method: 'GET',
            url: `${getBasePath(courseId)}`,
        });
        response.flush([]);
        await methodCall;
    });

    function getBasePath(courseId: number): string {
        return `${baseUrl}/courses/${courseId}/course-competencies`;
    }
});

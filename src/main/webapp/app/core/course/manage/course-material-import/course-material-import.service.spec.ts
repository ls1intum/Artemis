import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { CourseMaterialImportService } from './course-material-import.service';
import { CourseMaterialImportOptionsDTO, CourseMaterialImportResultDTO } from './course-material-import.model';

describe('CourseMaterialImportService', () => {
    let service: CourseMaterialImportService;
    let httpMock: HttpTestingController;

    const mockSummary: CourseSummaryDTO = {
        numberOfStudents: 100,
        numberOfTutors: 5,
        numberOfEditors: 2,
        numberOfInstructors: 3,
        numberOfParticipations: 500,
        numberOfSubmissions: 1000,
        numberOfResults: 800,
        numberOfConversations: 50,
        numberOfPosts: 200,
        numberOfAnswerPosts: 150,
        numberOfCompetencies: 10,
        numberOfCompetencyProgress: 1000,
        numberOfLearnerProfiles: 100,
        numberOfIrisChatSessions: 25,
        numberOfLLMTraces: 100,
        numberOfBuilds: 500,
        numberOfExams: 2,
        numberOfExercises: 15,
        numberOfProgrammingExercises: 5,
        numberOfTextExercises: 3,
        numberOfModelingExercises: 4,
        numberOfQuizExercises: 2,
        numberOfFileUploadExercises: 1,
        numberOfLectures: 8,
        numberOfFaqs: 12,
        numberOfTutorialGroups: 6,
    };

    const mockResult: CourseMaterialImportResultDTO = {
        exercisesImported: 10,
        lecturesImported: 5,
        examsImported: 2,
        competenciesImported: 8,
        tutorialGroupsImported: 4,
        faqsImported: 12,
        errors: [],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [CourseMaterialImportService, provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(CourseMaterialImportService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    describe('getImportSummary', () => {
        it('should call correct endpoint and return summary', () => {
            const targetCourseId = 1;
            const sourceCourseId = 2;

            service.getImportSummary(targetCourseId, sourceCourseId).subscribe((summary) => {
                expect(summary).toEqual(mockSummary);
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-summary/${sourceCourseId}`);
            expect(req.request.method).toBe('GET');
            req.flush(mockSummary);
        });

        it('should handle error response', () => {
            const targetCourseId = 1;
            const sourceCourseId = 2;

            service.getImportSummary(targetCourseId, sourceCourseId).subscribe({
                error: (error) => {
                    expect(error.status).toBe(403);
                },
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-summary/${sourceCourseId}`);
            req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });
        });
    });

    describe('importMaterial', () => {
        it('should call correct endpoint with options and return result', () => {
            const targetCourseId = 1;
            const options: CourseMaterialImportOptionsDTO = {
                sourceCourseId: 2,
                importExercises: true,
                importLectures: true,
                importExams: false,
                importCompetencies: true,
                importTutorialGroups: false,
                importFaqs: true,
            };

            service.importMaterial(targetCourseId, options).subscribe((result) => {
                expect(result).toEqual(mockResult);
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-material`);
            expect(req.request.method).toBe('POST');
            expect(req.request.body).toEqual(options);
            req.flush(mockResult);
        });

        it('should handle import with all options disabled', () => {
            const targetCourseId = 1;
            const options: CourseMaterialImportOptionsDTO = {
                sourceCourseId: 2,
                importExercises: false,
                importLectures: false,
                importExams: false,
                importCompetencies: false,
                importTutorialGroups: false,
                importFaqs: false,
            };

            const emptyResult: CourseMaterialImportResultDTO = {
                exercisesImported: 0,
                lecturesImported: 0,
                examsImported: 0,
                competenciesImported: 0,
                tutorialGroupsImported: 0,
                faqsImported: 0,
                errors: [],
            };

            service.importMaterial(targetCourseId, options).subscribe((result) => {
                expect(result).toEqual(emptyResult);
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-material`);
            req.flush(emptyResult);
        });

        it('should handle error response', () => {
            const targetCourseId = 1;
            const options: CourseMaterialImportOptionsDTO = {
                sourceCourseId: 2,
                importExercises: true,
                importLectures: false,
                importExams: false,
                importCompetencies: false,
                importTutorialGroups: false,
                importFaqs: false,
            };

            service.importMaterial(targetCourseId, options).subscribe({
                error: (error) => {
                    expect(error.status).toBe(400);
                },
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-material`);
            req.flush('Bad Request', { status: 400, statusText: 'Bad Request' });
        });

        it('should handle result with errors', () => {
            const targetCourseId = 1;
            const options: CourseMaterialImportOptionsDTO = {
                sourceCourseId: 2,
                importExercises: true,
                importLectures: false,
                importExams: false,
                importCompetencies: false,
                importTutorialGroups: false,
                importFaqs: false,
            };

            const resultWithErrors: CourseMaterialImportResultDTO = {
                exercisesImported: 8,
                lecturesImported: 0,
                examsImported: 0,
                competenciesImported: 0,
                tutorialGroupsImported: 0,
                faqsImported: 0,
                errors: ["Failed to import exercise 'Exercise 1': timeout", "Failed to import exercise 'Exercise 2': not found"],
            };

            service.importMaterial(targetCourseId, options).subscribe((result) => {
                expect(result.exercisesImported).toBe(8);
                expect(result.errors).toHaveLength(2);
            });

            const req = httpMock.expectOne(`api/core/courses/${targetCourseId}/import-material`);
            req.flush(resultWithErrors);
        });
    });
});

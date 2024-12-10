import { TestBed } from '@angular/core/testing';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

describe('ExerciseGroupService', () => {
    let service: ExerciseGroupService;
    let httpMock: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [ExerciseGroupService, provideHttpClient(), provideHttpClientTesting()],
        });

        service = TestBed.inject(ExerciseGroupService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should set additional parameters correctly in delete', () => {
        const courseId = 1;
        const examId = 2;
        const exerciseGroupId = 3;
        const deleteStudentReposBuildPlans = true;
        const deleteBaseReposBuildPlans = false;

        service.delete(courseId, examId, exerciseGroupId, deleteStudentReposBuildPlans, deleteBaseReposBuildPlans).subscribe();

        const req = httpMock.expectOne(
            `api/courses/${courseId}/exams/${examId}/exercise-groups/${exerciseGroupId}?deleteStudentReposBuildPlans=true&deleteBaseReposBuildPlans=false`,
        );

        expect(req.request.method).toBe('DELETE');
        expect(req.request.params.get('deleteStudentReposBuildPlans')).toBe('true');
        expect(req.request.params.get('deleteBaseReposBuildPlans')).toBe('false');

        req.flush(null); // Respond with no content for DELETE request
    });
});

import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseGradingStatistics } from 'app/entities/programming-exercise-test-case-statistics.model';

describe('ProgrammingExerciseTask Service', () => {
    let service: ProgrammingExerciseTaskService;
    let httpMock: HttpTestingController;

    const fakeAlertService: Partial<AlertService> = {};
    const fakeGradingService: Partial<ProgrammingExerciseGradingService> = {};

    const exercise: Partial<ProgrammingExercise> = {};
    const course: Partial<Course> = {};
    const gradingStatistics: ProgrammingExerciseGradingStatistics = {};

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [fakeAlertService, fakeGradingService],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseTaskService);
                httpMock = TestBed.inject(HttpTestingController);

                service.configure(exercise as ProgrammingExercise, course, gradingStatistics);
            });
    });

    it('should get tasks from server', () => {
        const resourceUrl = `${SERVER_API_URL}api/programming-exercises`;
        httpMock.expectOne(`${resourceUrl}/${exercise.id}/tasks`);
    });

    it('should create correct task settings from test cases', () => {});
    it('should assign tests correct grading statistics', () => {});
    it('should update task correctly', () => {});
    it('should save test case configuration', () => {});
    it('should reset test case configuration', () => {});
});

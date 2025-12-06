import { ActivatedRoute } from '@angular/router';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { QuizExerciseExportComponent } from 'app/quiz/manage/export/quiz-exercise-export.component';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService, TranslateStore } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

class QuizExerciseServiceStub {
    findForCourse = jest.fn();
    find = jest.fn();
    exportQuiz = jest.fn();
}

class CourseManagementServiceStub {
    find = jest.fn();
}

describe('QuizExerciseExportComponent', () => {
    let quizService: QuizExerciseServiceStub;
    let courseService: CourseManagementServiceStub;
    let alertService: AlertService;

    beforeEach(async () => {
        quizService = new QuizExerciseServiceStub();
        courseService = new CourseManagementServiceStub();

        await TestBed.configureTestingModule({
            imports: [QuizExerciseExportComponent],
            providers: [
                { provide: QuizExerciseService, useValue: quizService },
                { provide: CourseManagementService, useValue: courseService },
                { provide: AlertService, useValue: { error: jest.fn(), success: jest.fn(), addAlert: jest.fn() } as any },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: TranslateStore, useValue: {} },
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 42 }) },
                },
            ],
        }).compileComponents();

        alertService = TestBed.inject(AlertService);
    });

    it('should load course and questions on init', fakeAsync(() => {
        const course: Course = { id: 42 } as Course;
        const quiz = { id: 7, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        const quizDetails = { ...quiz, quizQuestions: [{ id: 1 } as QuizQuestion] } as QuizExercise;
        courseService.find.mockReturnValue(of(new ResponseStub(course)));
        quizService.findForCourse.mockReturnValue(of(new ResponseStub([quiz])));
        quizService.find.mockReturnValue(of(new ResponseStub(quizDetails)));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        tick();

        expect(courseService.find).toHaveBeenCalledWith(42);
        expect(quizService.findForCourse).toHaveBeenCalledWith(42);
        expect(quizService.find).toHaveBeenCalledWith(quiz.id);
        expect(fixture.componentInstance.questions).toHaveLength(1);
        expect(fixture.componentInstance.questions[0].exercise?.id).toBe(quiz.id);
    }));

    it('should forward export call', () => {
        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.componentInstance.questions = [{ id: 1 } as QuizQuestion];

        fixture.componentInstance.exportQuiz();

        expect(quizService.exportQuiz).toHaveBeenCalledWith([{ id: 1 }], false);
    });

    it('should handle load errors', fakeAsync(() => {
        courseService.find.mockReturnValue(of(new ResponseStub({ id: 42 } as Course)));
        quizService.findForCourse.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 400, statusText: 'boom' })));

        const fixture = TestBed.createComponent(QuizExerciseExportComponent);
        fixture.detectChanges();
        tick();

        expect(alertService.error).toHaveBeenCalledWith('error.http.400');
    }));
});

class ResponseStub<T> {
    constructor(public body: T) {}
}

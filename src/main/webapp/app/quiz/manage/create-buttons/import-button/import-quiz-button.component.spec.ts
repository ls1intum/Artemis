import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ImportQuizButtonComponent } from 'app/quiz/manage/create-buttons/import-button/import-quiz-button.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';

describe('QuizExercise Import Button Component', () => {
    let comp: ImportQuizButtonComponent;
    let fixture: ComponentFixture<ImportQuizButtonComponent>;
    let modalService: NgbModal;
    let router: Router;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: Router, useClass: MockRouter },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ImportQuizButtonComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        router = TestBed.inject(Router);
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open import modal', async () => {
        const promise = new Promise((resolve) => {
            resolve({ id: 456 } as Exercise);
        });
        const mockReturnValue = {
            result: promise,
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        const modalSpy = jest.spyOn(modalService, 'dismissAll');
        const routerSpy = jest.spyOn(router, 'navigate');

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.QUIZ);
        await expect(promise)
            .toResolve()
            .then(() => {
                expect(modalSpy).toHaveBeenCalledOnce();
                expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `quiz-exercises`, 456, 'import']);
            });
    });
});

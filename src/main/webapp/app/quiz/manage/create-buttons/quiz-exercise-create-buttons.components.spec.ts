import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { QuizExerciseCreateButtonsComponent } from 'app/quiz/manage/create-buttons/quiz-exercise-create-buttons.component';

describe('QuizExercise Create Buttons Component', () => {
    let comp: QuizExerciseCreateButtonsComponent;
    let fixture: ComponentFixture<QuizExerciseCreateButtonsComponent>;
    let modalService: NgbModal;

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
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(QuizExerciseCreateButtonsComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);

        comp.course = course;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open import modal', () => {
        const mockReturnValue = {
            result: Promise.resolve({ id: 456 } as QuizExercise),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.QUIZ);
    });
});

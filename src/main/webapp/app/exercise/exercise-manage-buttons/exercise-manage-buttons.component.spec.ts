import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseComponent } from 'app/quiz/manage/exercise/quiz-exercise.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseManageButtonsComponent } from 'app/exercise/exercise-manage-buttons/exercise-manage-buttons.component';

describe('Exercise Create Buttons Component', () => {
    let comp: ExerciseManageButtonsComponent;
    let fixture: ComponentFixture<ExerciseManageButtonsComponent>;

    const course = { id: 123 } as Course;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [QuizExerciseComponent],
            declarations: [],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseManageButtonsComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should create', () => {
        expect(comp).toBeTruthy();
    });
});

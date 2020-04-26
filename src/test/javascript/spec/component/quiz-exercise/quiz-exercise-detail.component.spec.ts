import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseDetailComponent } from 'app/exercises/quiz/manage/quiz-exercise-detail.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course);
    quizExercise.id = 456;

    const route = ({ snapshot: { paramMap: convertToParamMap({ courseId: course.id, exerciseId: quizExercise.id }) } } as any) as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseDetailComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(QuizExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(QuizExerciseDetailComponent);
        comp = fixture.componentInstance;
        courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
        quizExerciseService = fixture.debugElement.injector.get(QuizExerciseService);
    });

    describe('OnInit', () => {
        it('Should call courseExerciseService.find and quizExerciseService.find', () => {
            // GIVEN
            spyOn(courseManagementService, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: course,
                    }),
                ),
            );
            spyOn(quizExerciseService, 'find').and.returnValue(
                of(
                    new HttpResponse({
                        body: quizExercise,
                    }),
                ),
            );

            // WHEN
            comp.course = course;
            comp.ngOnInit();

            // THEN
            expect(courseManagementService.find).toHaveBeenCalled();
            expect(quizExerciseService.find).toHaveBeenCalled();
        });
    });
});

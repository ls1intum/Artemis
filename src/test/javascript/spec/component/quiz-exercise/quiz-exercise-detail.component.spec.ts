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
import * as moment from 'moment';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { AnswerOption } from 'app/entities/quiz/answer-option.model';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { stub } from 'sinon';

chai.use(sinonChai);
const expect = chai.expect;

describe('QuizExercise Management Detail Component', () => {
    let comp: QuizExerciseDetailComponent;
    let courseManagementService: CourseManagementService;
    let quizExerciseService: QuizExerciseService;
    let fixture: ComponentFixture<QuizExerciseDetailComponent>;

    const course: Course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    const mcQuestion = new MultipleChoiceQuestion();

    quizExercise.id = 456;
    quizExercise.title = 'test';
    quizExercise.duration = 600;
    mcQuestion.title = 'test';
    mcQuestion.answerOptions = [new AnswerOption()];
    quizExercise.quizQuestions = [mcQuestion];

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
            const quizExerciseServiceStub = stub(quizExerciseService, 'find');
            const courseManagementServiceStub = stub(courseManagementService, 'find');

            quizExerciseServiceStub.returns(
                of(
                    new HttpResponse<QuizExercise>({ body: quizExercise }),
                ),
            );
            courseManagementServiceStub.returns(
                of(
                    new HttpResponse<Course>({ body: course }),
                ),
            );

            // WHEN
            comp.course = course;
            comp.ngOnInit();

            // THEN
            expect(quizExerciseServiceStub).to.have.been.called;
            expect(courseManagementServiceStub).to.have.been.called;
        });
    });

    describe('onDurationChange', () => {
        // setup
        beforeEach(() => {
            comp.quizExercise = quizExercise;
        });

        it('Should update duration and quizExercise.duration with same values', () => {
            comp.duration = { minutes: 15, seconds: 30 };
            comp.onDurationChange();

            // compare duration with quizExercise.duration
            const durationAsSeconds = moment.duration(comp.duration).asSeconds();
            expect(durationAsSeconds).to.equal(comp.quizExercise.duration);
        });

        it('Should increase minutes when reaching 60 seconds', () => {
            comp.duration = { minutes: 0, seconds: 60 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(1);
            expect(comp.duration.seconds).to.equal(0);
        });

        it('Should decrease minutes when reaching -1 seconds', () => {
            comp.duration = { minutes: 1, seconds: -1 };
            comp.onDurationChange();

            expect(comp.duration.minutes).to.equal(0);
            expect(comp.duration.seconds).to.equal(59);
        });
    });
});

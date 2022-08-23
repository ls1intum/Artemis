import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { QuizExerciseComponent } from 'app/exercises/quiz/manage/quiz-exercise.component';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizBatch, QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { Course } from 'app/entities/course.model';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { AlertService } from 'app/core/util/alert.service';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { QuizExerciseImportComponent } from 'app/exercises/quiz/manage/quiz-exercise-import.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';

describe('QuizExercise Management Component', () => {
    let comp: QuizExerciseComponent;
    let fixture: ComponentFixture<QuizExerciseComponent>;
    let service: QuizExerciseService;
    let alertService: AlertService;
    let modalService: NgbModal;

    const course = { id: 123 } as Course;
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.id = 456;
    quizExercise.title = 'Quiz Exercise';
    const quizBatch = new QuizBatch();
    quizBatch.id = 567;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [QuizExerciseComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .overrideTemplate(QuizExerciseComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(QuizExerciseComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(QuizExerciseService);
        alertService = fixture.debugElement.injector.get(AlertService);
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.course = course;
        comp.quizExercises = [quizExercise];
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should call loadExercises on init', () => {
        // GIVEN
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'findForCourse').mockReturnValue(
            of(
                new HttpResponse({
                    body: [quizExercise],
                    headers,
                }),
            ),
        );

        // WHEN
        comp.ngOnInit();

        // THEN
        expect(service.findForCourse).toHaveBeenCalledOnce();
        expect(comp.quizExercises[0]).toEqual(quizExercise);
    });

    it('should reset quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'reset').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'findForCourse');

        comp.ngOnInit();
        comp.resetQuizExercise(456);
        expect(service.reset).toHaveBeenCalledWith(456);
        expect(service.reset).toHaveBeenCalledOnce();
    });

    it('should open modal', () => {
        const mockReturnValue = { result: Promise.resolve({ id: 456 } as QuizExercise) } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(QuizExerciseImportComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
    });

    it('should open quiz for practice', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'openForPractice').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.openForPractice(456);
        expect(service.openForPractice).toHaveBeenCalledWith(456);
        expect(service.openForPractice).toHaveBeenCalledOnce();
    });

    it('should not open quiz for practice on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'openForPractice').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.openForPractice(456);
        expect(service.openForPractice).toHaveBeenCalledWith(456);
        expect(service.openForPractice).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledOnce();
    });

    it('should start quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'start').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.startQuiz(456);
        expect(service.start).toHaveBeenCalledWith(456);
        expect(service.start).toHaveBeenCalledOnce();
    });

    it('should not start quiz on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'start').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.startQuiz(456);
        expect(service.start).toHaveBeenCalledWith(456);
        expect(service.start).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledOnce();
    });

    it('should end quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'end').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.endQuiz(456);
        expect(service.end).toHaveBeenCalledWith(456);
        expect(service.end).toHaveBeenCalledOnce();
    });

    it('should add quiz batch', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'addBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.addBatch(456);
        expect(service.addBatch).toHaveBeenCalledWith(456);
        expect(service.addBatch).toHaveBeenCalledOnce();
    });

    it('should start quiz batch', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'startBatch').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizBatch,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.startBatch(456, 567);
        expect(service.startBatch).toHaveBeenCalledWith(567);
        expect(service.startBatch).toHaveBeenCalledOnce();
    });

    it('should make quiz visible', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'setVisible').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.showQuiz(456);
        expect(service.setVisible).toHaveBeenCalledWith(456);
        expect(service.setVisible).toHaveBeenCalledOnce();
    });

    it('should not make quiz visible on error', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'setVisible').mockReturnValue(throwError(() => new HttpErrorResponse({ error: 'Forbidden', status: 403 })));
        jest.spyOn(alertService, 'error');

        comp.ngOnInit();
        comp.showQuiz(456);
        expect(service.setVisible).toHaveBeenCalledWith(456);
        expect(service.setVisible).toHaveBeenCalledOnce();
        expect(alertService.error).toHaveBeenCalledOnce();
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledOnce();
    });

    it('should delete quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'delete').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    headers,
                }),
            ),
        );

        comp.ngOnInit();
        comp.deleteQuizExercise(456);
        expect(service.delete).toHaveBeenCalledWith(456);
        expect(service.delete).toHaveBeenCalledOnce();
    });

    it('should export quiz', () => {
        const headers = new HttpHeaders().append('link', 'link;link');
        jest.spyOn(service, 'find').mockReturnValue(
            of(
                new HttpResponse({
                    body: quizExercise,
                    headers,
                }),
            ),
        );
        jest.spyOn(service, 'exportQuiz');

        comp.ngOnInit();
        comp.exportQuizById(456, true);
        expect(service.find).toHaveBeenCalledWith(456);
        expect(service.find).toHaveBeenCalledOnce();
        expect(service.exportQuiz).toHaveBeenCalledWith(undefined, true, 'Quiz Exercise');
        expect(service.exportQuiz).toHaveBeenCalledOnce();
    });

    it('should return quiz is over', () => {
        quizExercise.quizEnded = true;
        expect(comp.quizIsOver(quizExercise)).toBeTrue();
    });

    it('should return quiz is not over', () => {
        quizExercise.quizEnded = false;
        expect(comp.quizIsOver(quizExercise)).toBeFalse();
    });

    it('should return quiz id', () => {
        expect(comp.trackId(0, quizExercise)).toBe(456);
    });

    describe('QuizExercise Search Exercises', () => {
        it('should show all exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Quiz', '', 'quiz');

            // THEN
            expect(comp.quizExercises).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(1);
        });

        it('should show no exercises', () => {
            // WHEN
            comp.exerciseFilter = new ExerciseFilter('Prog', '', 'all');

            // THEN
            expect(comp.quizExercises).toHaveLength(1);
            expect(comp.filteredQuizExercises).toHaveLength(0);
        });
    });
});

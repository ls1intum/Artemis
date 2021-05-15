import { fakeAsync, getTestBed, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import * as chai from 'chai';
import { take } from 'rxjs/operators';
import { StudentQuestionService } from 'app/overview/student-questions/student-question/student-question.service';
import { StudentQuestion } from 'app/entities/student-question.model';
import { Course } from 'app/entities/course.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Lecture } from 'app/entities/lecture.model';

const expect = chai.expect;

describe('StudentQuestion Service', () => {
    let injector: TestBed;
    let service: StudentQuestionService;
    let httpMock: HttpTestingController;
    let elemDefault: StudentQuestion;
    let elem2: StudentQuestion;
    let courseDefault: Course;
    let exerciseDefault: TextExercise;
    let lectureDefault: Lecture;
    let studentQuestionList: StudentQuestion[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(StudentQuestionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new StudentQuestion();
        elemDefault.id = 0;
        elemDefault.creationDate = undefined;
        elemDefault.questionText = 'This is a test question';

        elem2 = new StudentQuestion();
        elem2.id = 1;
        elem2.creationDate = undefined;
        elem2.questionText = 'This is a test question';

        courseDefault = new Course();
        courseDefault.id = 1;

        exerciseDefault = new TextExercise(courseDefault, undefined);
        exerciseDefault.id = 1;
        exerciseDefault.studentQuestions = [elemDefault];

        lectureDefault = new Lecture();
        lectureDefault.id = 1;
        lectureDefault.studentQuestions = [elem2];

        courseDefault.exercises = [exerciseDefault];
        courseDefault.lectures = [lectureDefault];

        studentQuestionList = [elemDefault, elem2];
    });

    describe('Service methods', () => {
        it('should create a StudentQuestion', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(1, new StudentQuestion())
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should update a StudentQuestion', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, questionText: 'This is another test question' };

            const expected = { ...returnedFromService };
            service
                .update(1, expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should delete a StudentQuestion', fakeAsync(() => {
            service.delete(1, 123).subscribe((resp) => expect(resp.ok).to.be.true);

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
        }));

        it('should update the votes of a StudentQuestion', fakeAsync(() => {
            const returnedFromService = { ...elemDefault, votes: 42 };

            const expected = { ...returnedFromService };
            service
                .updateVotes(1, expected.id!, 0)
                .pipe(take(1))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should return all student questions for a course', fakeAsync(() => {
            const returnedFromService = [...studentQuestionList];

            const expected = [...studentQuestionList];
            service
                .findQuestionsForCourse(courseDefault.id!)
                .pipe(take(2))
                .subscribe((resp) => expect(resp.body).to.deep.equal(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});

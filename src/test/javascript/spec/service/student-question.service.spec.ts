import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
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
    let expectedResult: any;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        expectedResult = {} as HttpResponse<StudentQuestion>;
        injector = getTestBed();
        service = injector.get(StudentQuestionService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new StudentQuestion();
        elemDefault.id = 0;
        elemDefault.creationDate = undefined;
        elemDefault.questionText = 'This is a test question';

        elem2 = new StudentQuestion();
        elem2.id = 1;
        elem2.creationDate = null;
        elem2.questionText = 'This is a test question';

        exerciseDefault = new TextExercise();
        exerciseDefault.id = 1;
        exerciseDefault.studentQuestions = [elemDefault];

        lectureDefault = new Lecture();
        lectureDefault.id = 1;
        lectureDefault.studentQuestions = [elem2];

        courseDefault = new Course();
        courseDefault.id = 1;
        courseDefault.exercises = [exerciseDefault];
        courseDefault.lectures = [lectureDefault];

        studentQuestionList = [elemDefault, elem2];
    });

    describe('Service methods', () => {
        it('should create a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(new StudentQuestion())
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should update a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, questionText: 'This is another test question' };

            const expected = { ...returnedFromService };
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should delete a StudentQuestion', async () => {
            service.delete(123).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            expect(expectedResult).to.be.true;
        });

        it('should update the votes of a StudentQuestion', async () => {
            const returnedFromService = { ...elemDefault, votes: 42 };

            const expected = { ...returnedFromService };
            service
                .updateVotes(expected.id!, 0)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });

        it('should return all student questions for a course', async () => {
            const returnedFromService = [...studentQuestionList];

            const expected = [...studentQuestionList];
            service
                .findQuestionsForCourse(courseDefault.id)
                .pipe(take(2))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            expect(expectedResult.body).to.deep.equal(expected);
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

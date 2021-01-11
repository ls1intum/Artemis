import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import * as sinon from 'sinon';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { JhiSortByDirective, JhiSortDirective, JhiTranslateDirective } from 'ng-jhipster';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MomentModule } from 'ngx-moment';
import { CourseScoresComponent } from 'app/course/course-scores/course-scores.component';
import { ArtemisTestModule } from '../../../test.module';
import { Directive, Input } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { HttpResponse } from '@angular/common/http';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import * as moment from 'moment';
import { User } from 'app/core/user/user.model';
import { Result } from 'app/entities/result.model';

chai.use(sinonChai);
const expect = chai.expect;

// tslint:disable-next-line:directive-selector
@Directive({ selector: '[translateValues]' })
export class MockTranslateValuesDirective {
    @Input('translateValues') data: any;
}

describe('CourseScoresComponent', () => {
    let fixture: ComponentFixture<CourseScoresComponent>;
    let component: CourseScoresComponent;
    let courseService: CourseManagementService;

    const exerciseWithFutureReleaseDate = {
        title: 'exercise with future release date',
        releaseDate: moment().add(1, 'day'),
    } as Exercise;

    const overallPoints = 2 + 3 + 0 + 4;
    const exerciseMaxPointsPerType = new Map<ExerciseType, number[]>();
    const exerciseOne = {
        title: 'exercise one',
        id: 1,
        dueDate: moment().add(5, 'minutes'),
        type: ExerciseType.QUIZ,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxScore: 2,
    } as Exercise;
    const sharedDueDate = moment().add(4, 'minutes');
    const exerciseTwo = {
        title: 'exercise two',
        id: 2,
        dueDate: sharedDueDate,
        type: ExerciseType.QUIZ,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxScore: 3,
    } as Exercise;
    const exerciseThree = {
        title: 'exercise three',
        id: 3,
        dueDate: sharedDueDate,
        type: ExerciseType.FILE_UPLOAD,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
        maxScore: 1,
        bonusPoints: 0,
    } as Exercise;
    const exerciseFour = {
        title: 'exercise four',
        id: 4,
        dueDate: moment().add(2, 'minutes'),
        type: ExerciseType.MODELING,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxScore: 4,
    } as Exercise;

    const course = {
        courseId: 1,
        exercises: [exerciseWithFutureReleaseDate, exerciseFour, exerciseOne, exerciseThree, exerciseTwo],
    } as Course;

    const user1 = {
        id: 1,
    } as User;
    const user2 = {
        id: 2,
    } as User;
    const participation1 = {
        id: 1,
        student: user1,
        exercise: exerciseOne,
        results: [{ score: 50 } as Result],
    } as StudentParticipation;
    const participation2 = {
        id: 2,
        student: user1,
        exercise: exerciseTwo,
        results: [{ score: 66 } as Result],
    } as StudentParticipation;
    const participation3 = {
        id: 3,
        student: user1,
        exercise: exerciseThree,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation4 = {
        id: 4,
        student: user1,
        exercise: exerciseFour,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation5 = {
        id: 5,
        student: user2,
        exercise: exerciseOne,
        results: [],
    } as StudentParticipation;
    const participation6 = {
        id: 6,
        student: user2,
        exercise: exerciseTwo,
        results: [{ score: 0 } as Result],
    } as StudentParticipation;
    const participation7 = {
        id: 7,
        student: user2,
        exercise: exerciseThree,
        results: [{ score: 99 } as Result],
    } as StudentParticipation;
    const participation8 = {
        id: 8,
        student: user2,
        exercise: exerciseFour,
        results: [{ score: 2 } as Result],
    } as StudentParticipation;
    const participations: StudentParticipation[] = [participation1, participation2, participation3, participation4, participation5, participation6, participation7, participation8];
    const pointsOfStudent1 = new Map<ExerciseType, number[]>();
    const pointsOfStudent2 = new Map<ExerciseType, number[]>();

    beforeEach(() => {
        exerciseMaxPointsPerType.set(ExerciseType.QUIZ, [3, 2]);
        exerciseMaxPointsPerType.set(ExerciseType.FILE_UPLOAD, []);
        exerciseMaxPointsPerType.set(ExerciseType.MODELING, [4]);
        exerciseMaxPointsPerType.set(ExerciseType.PROGRAMMING, []);
        exerciseMaxPointsPerType.set(ExerciseType.TEXT, []);

        pointsOfStudent1.set(ExerciseType.QUIZ, [1.98, 1]);
        pointsOfStudent1.set(ExerciseType.FILE_UPLOAD, [1]);
        pointsOfStudent1.set(ExerciseType.MODELING, [4]);
        pointsOfStudent1.set(ExerciseType.PROGRAMMING, []);
        pointsOfStudent1.set(ExerciseType.TEXT, []);

        pointsOfStudent2.set(ExerciseType.QUIZ, [0, NaN]);
        pointsOfStudent2.set(ExerciseType.FILE_UPLOAD, [0.99]);
        pointsOfStudent2.set(ExerciseType.MODELING, [0.08]);
        pointsOfStudent2.set(ExerciseType.PROGRAMMING, []);
        pointsOfStudent2.set(ExerciseType.TEXT, []);

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MomentModule],
            declarations: [
                CourseScoresComponent,
                MockComponent(AlertComponent),
                MockPipe(TranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(OrionFilterDirective),
                MockDirective(JhiSortByDirective),
                MockDirective(JhiSortDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(JhiTranslateDirective),
                MockTranslateValuesDirective,
            ],
            providers: [{ provide: ActivatedRoute, useValue: { params: of({ courseId: 1 }) } }, MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseScoresComponent);
                component = fixture.componentInstance;
                courseService = fixture.debugElement.injector.get(CourseManagementService);
            });
    });

    afterEach(function () {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should filter and sort exercises', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();

        expect(component.course).to.equal(course);
        expect(component.exercisesOfCourseThatAreIncludedInScoreCalculation).to.deep.equal([exerciseFour, exerciseThree, exerciseTwo, exerciseOne]);
    });

    it('should group exercises and calculate exercise max score', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        fixture.detectChanges();

        expect(component.allParticipationsOfCourse).to.equal(participations);
        expect(component.maxNumberOfOverallPoints).to.deep.equal(overallPoints);
        expect(component.exerciseMaxPointsPerType).to.deep.equal(exerciseMaxPointsPerType);
    });

    it('should calculate per student score', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        fixture.detectChanges();

        expect(component.students[0].pointsPerExerciseType).to.deep.equal(pointsOfStudent1);
        expect(component.students[0].numberOfParticipatedExercises).to.equal(4);
        expect(component.students[0].numberOfSuccessfulExercises).to.equal(2);
        expect(component.students[0].overallPoints).to.equal(7.98);

        expect(component.students[1].pointsPerExerciseType).to.deep.equal(pointsOfStudent2);
        expect(component.students[1].numberOfParticipatedExercises).to.equal(3);
        expect(component.students[1].numberOfSuccessfulExercises).to.equal(0);
        expect(component.students[1].overallPoints).to.equal(1.07);

        expect(component.exportReady).to.be.true;
    });
});

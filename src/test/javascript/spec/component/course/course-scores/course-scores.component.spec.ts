import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { CourseScoresComponent, EMAIL_KEY, NAME_KEY, OVERALL_COURSE_POINTS_KEY, OVERALL_COURSE_SCORE_KEY, USERNAME_KEY } from 'app/course/course-scores/course-scores.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { round } from 'app/shared/util/utils';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { MomentModule } from 'ngx-moment';
import { of } from 'rxjs';
import * as sinon from 'sinon';
import { SinonStub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../../test.module';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep } from 'app/entities/grade-step.model';
import { MockTranslateValuesDirective } from '../../../helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('CourseScoresComponent', () => {
    let fixture: ComponentFixture<CourseScoresComponent>;
    let component: CourseScoresComponent;
    let courseService: CourseManagementService;
    let gradingSystemService: GradingSystemService;

    const exerciseWithFutureReleaseDate = {
        title: 'exercise with future release date',
        releaseDate: moment().add(1, 'day'),
    } as Exercise;

    const overallPoints = 10 + 10 + 10;
    const exerciseMaxPointsPerType = new Map<ExerciseType, number[]>();
    const textIncludedWith10Points10BonusPoints = {
        title: 'exercise', // testing duplicated titles
        id: 1,
        dueDate: moment().add(5, 'minutes'),
        type: ExerciseType.TEXT,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 10,
        bonusPoints: 10,
    } as Exercise;
    const sharedDueDate = moment().add(4, 'minutes');
    const quizIncludedWith10Points0BonusPoints = {
        title: 'exercise', // testing duplicated titles
        id: 2,
        dueDate: sharedDueDate,
        type: ExerciseType.QUIZ,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 10,
        bonusPoints: 0,
    } as Exercise;
    const fileBonusWith10Points0BonusPoints = {
        title: 'exercise three',
        id: 3,
        dueDate: sharedDueDate,
        type: ExerciseType.FILE_UPLOAD,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_AS_BONUS,
        maxPoints: 10,
        bonusPoints: 0,
    } as Exercise;
    const modelingIncludedWith10Points0BonusPoints = {
        title: 'exercise four',
        id: 4,
        dueDate: moment().add(2, 'minutes'),
        type: ExerciseType.MODELING,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 10,
        bonusPoints: 0,
    } as Exercise;
    const quizNotIncludedWith10Points0BonusPoints = {
        title: 'exercise five',
        id: 5,
        dueDate: sharedDueDate,
        type: ExerciseType.QUIZ,
        includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
        maxPoints: 10,
        bonusPoints: 0,
    } as Exercise;

    const course = {
        courseId: 1,
        exercises: [
            quizIncludedWith10Points0BonusPoints,
            exerciseWithFutureReleaseDate,
            quizNotIncludedWith10Points0BonusPoints,
            textIncludedWith10Points10BonusPoints,
            fileBonusWith10Points0BonusPoints,
            modelingIncludedWith10Points0BonusPoints,
        ],
    } as Course;

    const user1 = {
        name: 'user1',
        login: 'user1login',
        email: 'user1mail',
        id: 1,
    } as User;
    const user2 = {
        name: 'user2',
        login: 'user2login',
        email: 'user2mail',
        id: 2,
    } as User;
    const participation1 = {
        id: 1,
        student: user1,
        exercise: textIncludedWith10Points10BonusPoints,
        results: [{ score: 200 } as Result],
    } as StudentParticipation;
    const participation2 = {
        id: 2,
        student: user1,
        exercise: modelingIncludedWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation3 = {
        id: 3,
        student: user1,
        exercise: fileBonusWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation4 = {
        id: 4,
        student: user1,
        exercise: modelingIncludedWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation5 = {
        id: 5,
        student: user2,
        exercise: textIncludedWith10Points10BonusPoints,
        results: [],
    } as StudentParticipation;
    const participation6 = {
        id: 6,
        student: user2,
        exercise: modelingIncludedWith10Points0BonusPoints,
        results: [{ score: 50 } as Result],
    } as StudentParticipation;
    const participation7 = {
        id: 7,
        student: user2,
        exercise: fileBonusWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation8 = {
        id: 8,
        student: user2,
        exercise: modelingIncludedWith10Points0BonusPoints,
        results: [{ score: 50 } as Result],
    } as StudentParticipation;
    const courseScoreStudent1 = new ScoresDTO();
    courseScoreStudent1.studentId = user1.id;
    courseScoreStudent1.pointsAchieved = 40;
    courseScoreStudent1.studentLogin = user1.login;
    courseScoreStudent1.scoreAchieved = round((40 / 30) * 100, 1);
    const courseScoreStudent2 = new ScoresDTO();
    courseScoreStudent2.studentId = user2.id;
    courseScoreStudent2.pointsAchieved = 15;
    courseScoreStudent2.studentLogin = user2.login;
    courseScoreStudent2.scoreAchieved = round((15 / 30) * 100, 1);
    let findCourseScoresSpy: SinonStub;
    const participation9 = {
        id: 9,
        student: user1,
        exercise: quizNotIncludedWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participation10 = {
        id: 10,
        student: user2,
        exercise: quizNotIncludedWith10Points0BonusPoints,
        results: [{ score: 100 } as Result],
    } as StudentParticipation;
    const participations: StudentParticipation[] = [
        participation1,
        participation2,
        participation3,
        participation4,
        participation5,
        participation6,
        participation7,
        participation8,
        participation9,
        participation10,
    ];
    const pointsOfStudent1 = new Map<ExerciseType, number[]>();
    const pointsOfStudent2 = new Map<ExerciseType, number[]>();

    beforeEach(() => {
        exerciseMaxPointsPerType.set(ExerciseType.QUIZ, [10]);
        exerciseMaxPointsPerType.set(ExerciseType.FILE_UPLOAD, [10]);
        exerciseMaxPointsPerType.set(ExerciseType.MODELING, [10]);
        exerciseMaxPointsPerType.set(ExerciseType.PROGRAMMING, []);
        exerciseMaxPointsPerType.set(ExerciseType.TEXT, [10]);

        pointsOfStudent1.set(ExerciseType.QUIZ, [Number.NaN]);
        pointsOfStudent1.set(ExerciseType.FILE_UPLOAD, [10]);
        pointsOfStudent1.set(ExerciseType.MODELING, [10]);
        pointsOfStudent1.set(ExerciseType.PROGRAMMING, []);
        pointsOfStudent1.set(ExerciseType.TEXT, [20]);

        pointsOfStudent2.set(ExerciseType.QUIZ, [Number.NaN]);
        pointsOfStudent2.set(ExerciseType.FILE_UPLOAD, [10]);
        pointsOfStudent2.set(ExerciseType.MODELING, [5]);
        pointsOfStudent2.set(ExerciseType.PROGRAMMING, []);
        pointsOfStudent2.set(ExerciseType.TEXT, [Number.NaN]);

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MomentModule],
            declarations: [
                CourseScoresComponent,
                MockComponent(AlertComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(OrionFilterDirective),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(TranslateDirective),
                MockTranslateValuesDirective,
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 1 }) },
                },
                MockProvider(TranslateService),
                MockProvider(ParticipantScoresService),
                MockProvider(GradingSystemService, {
                    findGradingScaleForCourse: () => {
                        return of(
                            new HttpResponse({
                                body: new GradingScale(),
                                status: 200,
                            }),
                        );
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseScoresComponent);
                component = fixture.componentInstance;
                courseService = fixture.debugElement.injector.get(CourseManagementService);
                gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
                const participationScoreService = fixture.debugElement.injector.get(ParticipantScoresService);
                findCourseScoresSpy = sinon.stub(participationScoreService, 'findCourseScores').returns(of(new HttpResponse({ body: [courseScoreStudent1, courseScoreStudent2] })));
            });
    });

    afterEach(function () {
        quizIncludedWith10Points0BonusPoints.title = 'exercise'; // testing duplicated titles
        textIncludedWith10Points10BonusPoints.title = 'exercise'; // testing duplicated titles
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should not log error on sentry when correct participant score calculation', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        const errorSpy = sinon.spy(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.not.have.been.called;
    });

    it('should log error on sentry when missing participant score calculation', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        spyOn(gradingSystemService, 'findGradingScaleForCourse').and.returnValue(of(new HttpResponse({ status: 404 })));
        findCourseScoresSpy.returns(of(new HttpResponse({ body: [] })));
        const errorSpy = sinon.spy(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledTwice;
    });

    it('should log error on sentry when wrong points score calculation', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        spyOn(gradingSystemService, 'findGradingScaleForCourse').and.returnValue(of(new HttpResponse({ status: 404 })));
        const cs1 = new ScoresDTO();
        cs1.studentId = user1.id;
        cs1.pointsAchieved = 99;
        cs1.studentLogin = user1.login;
        cs1.scoreAchieved = round((40 / 30) * 100, 1);
        const cs2 = new ScoresDTO();
        cs2.studentId = user2.id;
        cs2.pointsAchieved = 99;
        cs2.studentLogin = user2.login;
        cs2.scoreAchieved = round((15 / 30) * 100, 1);
        findCourseScoresSpy.returns(of(new HttpResponse({ body: [cs1, cs2] })));
        const errorSpy = sinon.spy(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledTwice;
    });

    it('should log error on sentry when wrong score calculation', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        spyOn(gradingSystemService, 'findGradingScaleForCourse').and.returnValue(of(new HttpResponse({ status: 404 })));
        const cs1 = new ScoresDTO();
        cs1.studentId = user1.id;
        cs1.pointsAchieved = 40;
        cs1.studentLogin = user1.login;
        cs1.scoreAchieved = 99;
        const cs2 = new ScoresDTO();
        cs2.studentId = user2.id;
        cs2.pointsAchieved = 15;
        cs2.studentLogin = user2.login;
        cs2.scoreAchieved = 99;
        findCourseScoresSpy.returns(of(new HttpResponse({ body: [cs1, cs2] })));
        const errorSpy = sinon.spy(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).to.have.been.calledTwice;
    });
    it('should filter and sort exercises', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();

        expect(component.course).to.equal(course);
        expect(component.exercisesOfCourseThatAreIncludedInScoreCalculation).to.deep.equal([
            modelingIncludedWith10Points0BonusPoints,
            quizIncludedWith10Points0BonusPoints,
            fileBonusWith10Points0BonusPoints,
            textIncludedWith10Points10BonusPoints,
        ]);
    });

    it('should make duplicated titles unique', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();
        expect(quizIncludedWith10Points0BonusPoints.title).to.equal(`exercise (id=2)`);
        expect(textIncludedWith10Points10BonusPoints.title).to.equal(`exercise (id=1)`);
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
        expect(component.students[0].numberOfParticipatedExercises).to.equal(3);
        expect(component.students[0].numberOfSuccessfulExercises).to.equal(3);
        expect(component.students[0].overallPoints).to.equal(40);

        expect(component.students[1].pointsPerExerciseType).to.deep.equal(pointsOfStudent2);
        expect(component.students[1].numberOfParticipatedExercises).to.equal(2);
        expect(component.students[1].numberOfSuccessfulExercises).to.equal(1);
        expect(component.students[1].overallPoints).to.equal(15);

        expect(component.exportReady).to.be.true;
    });

    it('should generate csv correctly', () => {
        spyOn(courseService, 'findWithExercises').and.returnValue(of(new HttpResponse({ body: course })));
        spyOn(courseService, 'findAllParticipationsWithResults').and.returnValue(of(participations));
        fixture.detectChanges();
        const exportAsCsvStub = sinon.stub(component, 'exportAsCsv');
        component.exportResults();
        const generatedRows = exportAsCsvStub.getCall(0).args[0];
        const user1Row = generatedRows[0];
        validateUserRow(user1Row, user1.name!, user1.login!, user1.email!, '0', '0%', '10', '100%', '20', '200%', '10', '0%', '40', '133.3%');
        const user2Row = generatedRows[1];
        validateUserRow(user2Row, user2.name!, user2.login!, user2.email!, '0', '0%', '5', '50%', '0', '0%', '10', '0%', '15', '50%');
        const maxRow = generatedRows[3];
        expect(maxRow[OVERALL_COURSE_POINTS_KEY]).to.equal('30');
    });

    it('should set grading scale properties correctly', () => {
        const gradeStep: GradeStep = {
            gradeName: 'A',
            lowerBoundInclusive: true,
            lowerBoundPercentage: 0,
            upperBoundInclusive: true,
            upperBoundPercentage: 100,
            isPassingGrade: true,
        };
        const gradingScale: GradingScale = {
            gradeType: GradeType.GRADE,
            gradeSteps: [gradeStep],
        };
        spyOn(gradingSystemService, 'sortGradeSteps').and.returnValue([gradeStep]);
        spyOn(gradingSystemService, 'maxGrade').and.returnValue('A');
        spyOn(gradingSystemService, 'findMatchingGradeStep').and.returnValue(gradeStep);

        component.calculateGradingScaleInformation(gradingScale);

        expect(component.gradingScaleExists).to.be.true;
        expect(component.gradingScale).to.equal(gradingScale);
        expect(component.isBonus).to.be.false;
        expect(component.maxGrade).to.equal('A');
        expect(component.averageGrade).to.equal('A');
    });

    function validateUserRow(
        userRow: any,
        expectedName: string,
        expectedUsername: string,
        expectedEmail: string,
        expectedQuizPoints: string,
        expectedQuizScore: string,
        expectedModelingPoints: string,
        expectedModelingScore: string,
        expectedTextPoints: string,
        expectedTextScore: string,
        expectedFileUploadPoints: string,
        expectedFileUploadScore: string,
        expectedOverallCoursePoints: string,
        expectedOverallCourseScore: string,
    ) {
        expect(userRow[NAME_KEY]).to.equal(expectedName);
        expect(userRow[USERNAME_KEY]).to.equal(expectedUsername);
        expect(userRow[EMAIL_KEY]).to.equal(expectedEmail);
        expect(userRow['Quiz Points']).to.equal(expectedQuizPoints);
        expect(userRow['Quiz Score']).to.equal(expectedQuizScore);
        expect(userRow['Modeling Points']).to.equal(expectedModelingPoints);
        expect(userRow['Modeling Score']).to.equal(expectedModelingScore);
        expect(userRow['File-upload Points']).to.equal(expectedFileUploadPoints);
        expect(userRow['File-upload Score']).to.equal(expectedFileUploadScore);
        expect(userRow[OVERALL_COURSE_POINTS_KEY]).to.equal(expectedOverallCoursePoints);
        expect(userRow[OVERALL_COURSE_SCORE_KEY]).to.equal(expectedOverallCourseScore);
    }
});

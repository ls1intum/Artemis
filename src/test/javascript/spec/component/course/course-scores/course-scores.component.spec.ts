import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { User } from 'app/core/user/user.model';
import {
    CourseScoresComponent,
    EMAIL_KEY,
    HighlightType,
    NAME_KEY,
    OVERALL_COURSE_POINTS_KEY,
    OVERALL_COURSE_SCORE_KEY,
    USERNAME_KEY,
} from 'app/course/course-scores/course-scores.component';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { OrionFilterDirective } from 'app/shared/orion/orion-filter.directive';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradeStep } from 'app/entities/grade-step.model';
import { MockTranslateValuesDirective } from '../../../helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';

describe('CourseScoresComponent', () => {
    let fixture: ComponentFixture<CourseScoresComponent>;
    let component: CourseScoresComponent;
    let courseService: CourseManagementService;
    let gradingSystemService: GradingSystemService;

    const exerciseWithFutureReleaseDate = {
        title: 'exercise with future release date',
        releaseDate: dayjs().add(1, 'day'),
        id: 6,
        type: ExerciseType.TEXT,
        includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
        maxPoints: 10,
    } as Exercise;

    const overallPoints = 10 + 10 + 10;
    const exerciseMaxPointsPerType = new Map<ExerciseType, number[]>();
    const textIncludedWith10Points10BonusPoints = {
        title: 'exercise', // testing duplicated titles
        id: 1,
        dueDate: dayjs().add(5, 'minutes'),
        type: ExerciseType.TEXT,
        includedInOverallScore: IncludedInOverallScore.INCLUDED_COMPLETELY,
        maxPoints: 10,
        bonusPoints: 10,
    } as Exercise;
    const sharedDueDate = dayjs().add(4, 'minutes');
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
        dueDate: dayjs().add(2, 'minutes'),
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
        accuracyOfScores: 1,
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
    courseScoreStudent1.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(40 / 30, course);
    const courseScoreStudent2 = new ScoresDTO();
    courseScoreStudent2.studentId = user2.id;
    courseScoreStudent2.pointsAchieved = 15;
    courseScoreStudent2.studentLogin = user2.login;
    courseScoreStudent2.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(15 / 30, course);
    let findCourseScoresSpy: jest.SpyInstance;
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

        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseScoresComponent,
                MockComponent(ParticipantScoresDistributionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockDirective(OrionFilterDirective),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(DeleteButtonDirective),
                MockDirective(TranslateDirective),
                MockDirective(NgbTooltip),
                MockTranslateValuesDirective,
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { params: of({ courseId: 1 }) },
                },
                { provide: TranslateService, useClass: MockTranslateService },
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
                findCourseScoresSpy = jest
                    .spyOn(participationScoreService, 'findCourseScores')
                    .mockReturnValue(of(new HttpResponse({ body: [courseScoreStudent1, courseScoreStudent2] })));
            });
    });

    afterEach(() => {
        quizIncludedWith10Points0BonusPoints.title = 'exercise'; // testing duplicated titles
        textIncludedWith10Points10BonusPoints.title = 'exercise'; // testing duplicated titles
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    it('should not log error on sentry when correct participant score calculation', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        const errorSpy = jest.spyOn(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(0);
    });

    it('should log error on sentry when missing participant score calculation', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScale>({ status: 404 })));
        findCourseScoresSpy.mockReturnValue(of(new HttpResponse({ body: [] })));
        const errorSpy = jest.spyOn(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(2);
    });

    it('should log error on sentry when wrong points score calculation', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScale>({ status: 404 })));
        const cs1 = new ScoresDTO();
        cs1.studentId = user1.id;
        cs1.pointsAchieved = 99;
        cs1.studentLogin = user1.login;
        cs1.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(40 / 30, course);
        const cs2 = new ScoresDTO();
        cs2.studentId = user2.id;
        cs2.pointsAchieved = 99;
        cs2.studentLogin = user2.login;
        cs2.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(15 / 30, course);
        findCourseScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2] })));
        const errorSpy = jest.spyOn(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(2);
    });

    it('should log error on sentry when wrong score calculation', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScale>({ status: 404 })));
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
        findCourseScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2] })));
        const errorSpy = jest.spyOn(component, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(2);
    });
    it('should filter and sort exercises', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();

        expect(component.course).toEqual(course);
        expect(component.exercisesOfCourseThatAreIncludedInScoreCalculation).toEqual([
            modelingIncludedWith10Points0BonusPoints,
            quizIncludedWith10Points0BonusPoints,
            fileBonusWith10Points0BonusPoints,
            textIncludedWith10Points10BonusPoints,
        ]);
    });

    it('should make duplicated titles unique', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();
        expect(quizIncludedWith10Points0BonusPoints.title).toEqual(`exercise (id=2)`);
        expect(textIncludedWith10Points10BonusPoints.title).toEqual(`exercise (id=1)`);
    });

    it('should group exercises and calculate exercise max score', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        fixture.detectChanges();

        expect(component.allParticipationsOfCourse).toEqual(participations);
        expect(component.maxNumberOfOverallPoints).toEqual(overallPoints);
        expect(component.exerciseMaxPointsPerType).toEqual(exerciseMaxPointsPerType);
    });

    it('should calculate per student score', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        fixture.detectChanges();

        expect(component.students[0].pointsPerExerciseType).toEqual(pointsOfStudent1);
        expect(component.students[0].numberOfParticipatedExercises).toEqual(3);
        expect(component.students[0].numberOfSuccessfulExercises).toEqual(3);
        expect(component.students[0].overallPoints).toEqual(40);

        expect(component.students[1].pointsPerExerciseType).toEqual(pointsOfStudent2);
        expect(component.students[1].numberOfParticipatedExercises).toEqual(2);
        expect(component.students[1].numberOfSuccessfulExercises).toEqual(1);
        expect(component.students[1].overallPoints).toEqual(15);

        expect(component.exportReady).toBeTrue();
    });

    it('should generate csv correctly', () => {
        jest.spyOn(courseService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseService, 'findAllParticipationsWithResults').mockReturnValue(of(participations));
        fixture.detectChanges();
        const exportAsCsvStub = jest.spyOn(component, 'exportAsCsv').mockImplementation();
        component.exportResults();
        const generatedRows = exportAsCsvStub.mock.calls[0][1];
        const user1Row = generatedRows[0];
        validateUserRow(
            user1Row,
            user1.name!,
            user1.login!,
            user1.email!,
            '0',
            '0%',
            '10',
            '10',
            '100%',
            '20',
            '200%',
            '10',
            '0%',
            '40',
            roundScorePercentSpecifiedByCourseSettings(40 / 30, course).toLocaleString() + '%',
        );
        const user2Row = generatedRows[1];
        validateUserRow(user2Row, user2.name!, user2.login!, user2.email!, '0', '0%', '5', '5', '50%', '0', '0%', '10', '0%', '15', '50%');
        const maxRow = generatedRows[3];
        expect(maxRow[OVERALL_COURSE_POINTS_KEY]).toEqual('30');
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
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue([gradeStep]);
        jest.spyOn(gradingSystemService, 'maxGrade').mockReturnValue('A');
        jest.spyOn(gradingSystemService, 'findMatchingGradeStep').mockReturnValue(gradeStep);

        component.calculateGradingScaleInformation(gradingScale);

        expect(component.gradingScaleExists).toBeTrue();
        expect(component.gradingScale).toEqual(gradingScale);
        expect(component.isBonus).toBeFalse();
        expect(component.maxGrade).toEqual('A');
        expect(component.averageGrade).toEqual('A');
    });

    it('should set highlighting to default if current highlighting is deselected', () => {
        component.highlightedType = HighlightType.AVERAGE;

        // we deselect the current highlighting
        component.highlightBar(HighlightType.AVERAGE);

        expect(component.highlightedType).toBe(HighlightType.NONE);
        expect(component.valueToHighlight).toBe(undefined);
    });

    it('should highlight the median correctly', () => {
        component.highlightedType = HighlightType.AVERAGE;
        component.medianScoreIncluded = 55.5;

        // we select the median
        component.highlightBar(HighlightType.MEDIAN);

        expect(component.highlightedType).toBe(HighlightType.MEDIAN);
        expect(component.valueToHighlight).toBe(55.5);
    });

    function validateUserRow(
        userRow: any,
        expectedName: string,
        expectedUsername: string,
        expectedEmail: string,
        expectedQuizPoints: string,
        expectedQuizScore: string,
        expectedScoreModelingExercise: string,
        expectedModelingPoints: string,
        expectedModelingScore: string,
        expectedTextPoints: string,
        expectedTextScore: string,
        expectedFileUploadPoints: string,
        expectedFileUploadScore: string,
        expectedOverallCoursePoints: string,
        expectedOverallCourseScore: string,
    ) {
        expect(userRow[NAME_KEY]).toEqual(expectedName);
        expect(userRow[USERNAME_KEY]).toEqual(expectedUsername);
        expect(userRow[EMAIL_KEY]).toEqual(expectedEmail);
        expect(userRow['Quiz Points']).toEqual(expectedQuizPoints);
        expect(userRow['Quiz Score']).toEqual(expectedQuizScore);
        expect(userRow['exercise four']).toEqual(expectedScoreModelingExercise);
        expect(userRow['Modeling Points']).toEqual(expectedModelingPoints);
        expect(userRow['Modeling Score']).toEqual(expectedModelingScore);
        expect(userRow['File-upload Points']).toEqual(expectedFileUploadPoints);
        expect(userRow['File-upload Score']).toEqual(expectedFileUploadScore);
        expect(userRow[OVERALL_COURSE_POINTS_KEY]).toEqual(expectedOverallCoursePoints);
        expect(userRow[OVERALL_COURSE_SCORE_KEY]).toEqual(expectedOverallCourseScore);
    }
});

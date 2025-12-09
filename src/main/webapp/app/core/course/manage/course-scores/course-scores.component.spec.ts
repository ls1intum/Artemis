import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockLanguageHelper, MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { User } from 'app/core/user/user.model';
import { CourseScoresComponent, HighlightType } from 'app/core/course/manage/course-scores/course-scores.component';
import {
    COURSE_OVERALL_POINTS_KEY,
    COURSE_OVERALL_SCORE_KEY,
    EMAIL_KEY,
    NAME_KEY,
    PRESENTATION_POINTS_KEY,
    PRESENTATION_SCORE_KEY,
    USERNAME_KEY,
} from 'app/shared/export/export-constants';
import { CourseGradeInformationDTO, CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradingSystemService } from 'app/assessment/manage/grading-system/grading-system.service';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings } from 'app/shared/export/modal/export-modal.component';
import { ExportButtonComponent } from 'app/shared/export/button/export-button.component';
import { CommonSpreadsheetCellObject } from 'app/shared/export/row-builder/excel-export-row-builder';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { PlagiarismCasesService } from 'app/plagiarism/shared/services/plagiarism-cases.service';
import { PlagiarismCaseDTO } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ExerciseTypeStatisticsMap } from 'app/core/course/manage/course-scores/exercise-type-statistics-map';
import { MODULE_FEATURE_PLAGIARISM } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from '../../../layouts/profiles/profile-info.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CourseScoresComponent', () => {
    let fixture: ComponentFixture<CourseScoresComponent>;
    let component: CourseScoresComponent;
    let courseManagementService: CourseManagementService;
    let gradingSystemService: GradingSystemService;
    let plagiarismCasesService: PlagiarismCasesService;
    let profileService: ProfileService;

    const exerciseWithFutureReleaseDate = {
        title: 'exercise with future release date',
        releaseDate: dayjs().add(1, 'day'),
        id: 6,
        type: ExerciseType.TEXT,
        includedInOverallScore: IncludedInOverallScore.NOT_INCLUDED,
        maxPoints: 10,
    } as Exercise;

    const overallPoints = 10 + 10 + 10;
    const exerciseMaxPointsPerType = new ExerciseTypeStatisticsMap();
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
    const modelingNotIncludedWith10Points0BonusPoints = {
        title: 'exercise six',
        id: 6,
        dueDate: sharedDueDate,
        type: ExerciseType.MODELING,
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
            modelingNotIncludedWith10Points0BonusPoints,
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
        submissions: [{ results: [{ score: 200 } as Result] }],
        presentationScore: 100,
    } as StudentParticipation;
    const courseScoreStudent1 = new ScoresDTO();
    courseScoreStudent1.studentId = user1.id;
    courseScoreStudent1.pointsAchieved = 50;
    courseScoreStudent1.studentLogin = user1.login;
    courseScoreStudent1.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(50 / 40, course);
    const courseScoreStudent2 = new ScoresDTO();
    courseScoreStudent2.studentId = user2.id;
    courseScoreStudent2.pointsAchieved = 15;
    courseScoreStudent2.studentLogin = user2.login;
    courseScoreStudent2.scoreAchieved = roundScorePercentSpecifiedByCourseSettings(15 / 40, course);

    const createGradeScore = (participationId: number, userId: number, exerciseId: number, score: number, presentationScore = 0) => ({
        participationId,
        userId,
        exerciseId,
        score,
        presentationScore,
    });
    const courseGradeInformation: CourseGradeInformationDTO = {
        gradeScores: [
            createGradeScore(1, 1, 1, 200, 100),
            createGradeScore(2, 1, 4, 100, 0),
            createGradeScore(3, 1, 3, 100, 0),
            createGradeScore(4, 1, 4, 100, 0),
            createGradeScore(6, 2, 4, 50, 0),
            createGradeScore(7, 2, 3, 100, 0),
            createGradeScore(8, 2, 4, 50, 0),
            createGradeScore(9, 1, 5, 100, 0),
            createGradeScore(10, 2, 5, 100, 0),
            createGradeScore(11, 1, 6, 100, 100),
            createGradeScore(12, 2, 6, 100, 0),
        ],

        students: [
            {
                id: 1,
                login: 'user1login',
                firstName: 'user1',
                lastName: '',
                name: 'user1',
                email: 'user1mail',
            },
            {
                id: 2,
                login: 'user2login',
                firstName: 'user2',
                lastName: '',
                name: 'user2',
                email: 'user2mail',
            },
        ],
    };
    const pointsOfStudent1 = new ExerciseTypeStatisticsMap();
    const pointsOfStudent2 = new ExerciseTypeStatisticsMap();

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
    const gradingScaleWithGradedPresentations: GradingScale = {
        gradeType: GradeType.GRADE,
        gradeSteps: [gradeStep],
        presentationsNumber: 2,
        presentationsWeight: 25,
    };

    const setupMocks = () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScale>({ body: gradingScaleWithGradedPresentations })));
        jest.spyOn(plagiarismCasesService, 'getCoursePlagiarismCasesForScores').mockReturnValue(of(new HttpResponse<PlagiarismCaseDTO[]>({ body: [] })));
        fixture.detectChanges();
    };

    beforeEach(() => {
        exerciseMaxPointsPerType.setValue(ExerciseType.QUIZ, quizIncludedWith10Points0BonusPoints, 10);
        exerciseMaxPointsPerType.setValue(ExerciseType.FILE_UPLOAD, fileBonusWith10Points0BonusPoints, 10);
        exerciseMaxPointsPerType.setValue(ExerciseType.MODELING, modelingIncludedWith10Points0BonusPoints, 10);
        exerciseMaxPointsPerType.set(ExerciseType.PROGRAMMING, new Map());
        exerciseMaxPointsPerType.setValue(ExerciseType.TEXT, textIncludedWith10Points10BonusPoints, 10);

        pointsOfStudent1.setValue(ExerciseType.QUIZ, quizIncludedWith10Points0BonusPoints, Number.NaN);
        pointsOfStudent1.setValue(ExerciseType.FILE_UPLOAD, fileBonusWith10Points0BonusPoints, 10);
        pointsOfStudent1.setValue(ExerciseType.MODELING, modelingIncludedWith10Points0BonusPoints, 10);
        pointsOfStudent1.set(ExerciseType.PROGRAMMING, new Map());
        pointsOfStudent1.setValue(ExerciseType.TEXT, textIncludedWith10Points10BonusPoints, 20);

        pointsOfStudent2.setValue(ExerciseType.QUIZ, quizIncludedWith10Points0BonusPoints, Number.NaN);
        pointsOfStudent2.setValue(ExerciseType.FILE_UPLOAD, fileBonusWith10Points0BonusPoints, 10);
        pointsOfStudent2.setValue(ExerciseType.MODELING, modelingIncludedWith10Points0BonusPoints, 5);
        pointsOfStudent2.set(ExerciseType.PROGRAMMING, new Map());
        pointsOfStudent2.setValue(ExerciseType.TEXT, textIncludedWith10Points10BonusPoints, Number.NaN);

        return TestBed.configureTestingModule({
            imports: [MockModule(NgbTooltipModule), FaIconComponent],
            declarations: [
                CourseScoresComponent,
                MockComponent(ParticipantScoresDistributionComponent),
                MockComponent(ExportButtonComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
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
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
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
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseScoresComponent);
                component = fixture.componentInstance;
                courseManagementService = TestBed.inject(CourseManagementService);
                gradingSystemService = TestBed.inject(GradingSystemService);
                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                jest.spyOn(courseManagementService, 'findGradeScores').mockReturnValue(of(courseGradeInformation));
                profileService = fixture.debugElement.injector.get(ProfileService);
                const profileInfo = { activeModuleFeatures: [MODULE_FEATURE_PLAGIARISM] } as ProfileInfo;
                const getProfileInfoMock = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoMock.mockReturnValue(profileInfo);
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

    it('should filter and sort exercises', () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();

        expect(component.course).toEqual(course);
        expect(component.includedExercises).toEqual([
            modelingIncludedWith10Points0BonusPoints,
            quizIncludedWith10Points0BonusPoints,
            fileBonusWith10Points0BonusPoints,
            textIncludedWith10Points10BonusPoints,
        ]);
    });

    it('should make duplicated titles unique', () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        fixture.detectChanges();
        expect(quizIncludedWith10Points0BonusPoints.title).toBe(`exercise (id=2)`);
        expect(textIncludedWith10Points10BonusPoints.title).toBe(`exercise (id=1)`);
    });

    it('should group exercises and calculate exercise max score', () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(plagiarismCasesService, 'getCoursePlagiarismCasesForScores').mockReturnValue(of(new HttpResponse<PlagiarismCaseDTO[]>({ body: [] })));
        fixture.detectChanges();

        expect(component.gradeScores).toEqual(courseGradeInformation.gradeScores);
        expect(component.maxNumberOfOverallPoints).toEqual(overallPoints);
        expect(component.exerciseMaxPointsPerType).toEqual(exerciseMaxPointsPerType);
    });

    it('should calculate per student score', () => {
        setupMocks();

        expect(component.studentStatistics[0].pointsPerExerciseType).toEqual(pointsOfStudent1);
        expect(component.studentStatistics[0].numberOfParticipatedExercises).toBe(3);
        expect(component.studentStatistics[0].numberOfSuccessfulExercises).toBe(3);
        expect(component.studentStatistics[0].presentationPoints).toBe(10);
        expect(component.studentStatistics[0].overallPoints).toBe(50);

        expect(component.studentStatistics[1].pointsPerExerciseType).toEqual(pointsOfStudent2);
        expect(component.studentStatistics[1].numberOfParticipatedExercises).toBe(2);
        expect(component.studentStatistics[1].numberOfSuccessfulExercises).toBe(1);
        expect(component.studentStatistics[1].presentationPoints).toBe(0);
        expect(component.studentStatistics[1].overallPoints).toBe(15);

        expect(component.maxNumberOfPresentationPoints).toBe(10);
        expect(component.averageNumberOfPresentationPoints).toBe(5);

        expect(component.exportReady).toBeTrue();
    });

    it('should omit student statistics with no participations', () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(courseManagementService, 'findGradeScores').mockReturnValue(of({} as CourseGradeInformationDTO));
        jest.spyOn(plagiarismCasesService, 'getCoursePlagiarismCasesForScores').mockReturnValue(of(new HttpResponse<PlagiarismCaseDTO[]>({ body: [] })));

        fixture.detectChanges();

        expect(component.studentStatistics).toBeEmpty();
    });

    it('should assign plagiarism grade if there is a PLAGIARISM verdict', () => {
        jest.spyOn(courseManagementService, 'findWithExercises').mockReturnValue(of(new HttpResponse({ body: course })));
        jest.spyOn(gradingSystemService, 'findGradingScaleForCourse').mockReturnValue(of(new HttpResponse<GradingScale>({ body: Object.assign({}, gradingScale) })));
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue(gradingScale.gradeSteps);
        const matchingGradeStep = gradingScale.gradeSteps[0];
        jest.spyOn(gradingSystemService, 'findMatchingGradeStep').mockReturnValue(matchingGradeStep);
        jest.spyOn(plagiarismCasesService, 'getCoursePlagiarismCasesForScores').mockReturnValue(
            of(
                new HttpResponse<PlagiarismCaseDTO[]>({
                    body: [
                        {
                            id: 10,
                            verdict: PlagiarismVerdict.PLAGIARISM,
                            studentId: participation1.student!.id,
                        },
                    ],
                }),
            ),
        );

        fixture.detectChanges();

        expect(component.studentStatistics[0].gradeStep?.gradeName).toEqual(GradingScale.DEFAULT_PLAGIARISM_GRADE);
        expect(component.studentStatistics[1].gradeStep?.gradeName).toEqual(matchingGradeStep.gradeName);
        expect(component.averageGrade).toEqual(matchingGradeStep.gradeName);
    });

    it('should generate excel row correctly', () => {
        setupMocks();
        const exportAsExcelStub = jest.spyOn(component, 'exportAsExcel').mockImplementation();
        component.exportResults();
        const generatedRows = exportAsExcelStub.mock.calls[0][1];
        const user1Row = generatedRows[0];
        validateUserExportRow(
            user1Row,
            user1.name!,
            user1.login!,
            user1.email!,
            { t: 'n', v: 0 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 10 },
            { t: 'n', v: 10 },
            { t: 'n', v: 1, z: '0%' },
            { t: 'n', v: 20 },
            { t: 'n', v: 2, z: '0%' },
            { t: 'n', v: 10 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 10 },
            { t: 'n', v: 1, z: '0%' },
            { t: 'n', v: 50 },
            { t: 'n', v: 1.25, z: '0%' },
        );
        const user2Row = generatedRows[1];
        validateUserExportRow(
            user2Row,
            user2.name!,
            user2.login!,
            user2.email!,
            { t: 'n', v: 0 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 5 },
            { t: 'n', v: 5 },
            { t: 'n', v: 0.5, z: '0%' },
            { t: 'n', v: 0 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 10 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 0 },
            { t: 'n', v: 0, z: '0%' },
            { t: 'n', v: 15 },
            { t: 'n', v: 0.375, z: '0.0%' },
        );
        const maxRow = generatedRows[3];
        expect(maxRow[COURSE_OVERALL_POINTS_KEY]).toEqual({ t: 'n', v: 40 });
    });

    it('should generate csv correctly', () => {
        setupMocks();
        const exportAsCsvStub = jest.spyOn(component, 'exportAsCsv').mockImplementation();
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: true,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };
        component.exportResults(testOptions);
        const generatedRows = exportAsCsvStub.mock.calls[0][1];
        const user1Row = generatedRows[0];
        validateUserExportRow(
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
            '10',
            '100%',
            '50',
            roundScorePercentSpecifiedByCourseSettings(50 / 40, course).toLocaleString() + '%',
        );
        const user2Row = generatedRows[1];
        validateUserExportRow(user2Row, user2.name!, user2.login!, user2.email!, '0', '0%', '5', '5', '50%', '0', '0%', '10', '0%', '0', '0%', '15', '37.5%');
        const maxRow = generatedRows[3];
        expect(maxRow[COURSE_OVERALL_POINTS_KEY]).toBe('40');
    });

    it('should set grading scale properties correctly', () => {
        jest.spyOn(gradingSystemService, 'sortGradeSteps').mockReturnValue([gradeStep]);
        jest.spyOn(gradingSystemService, 'maxGrade').mockReturnValue('A');
        jest.spyOn(gradingSystemService, 'findMatchingGradeStep').mockReturnValue(gradeStep);

        component.setUpGradingScale(gradingScale);
        component.calculateGradingScaleInformation();

        expect(component.gradingScaleExists).toBeTrue();
        expect(component.gradingScale).toEqual(gradingScale);
        expect(component.isBonus).toBeFalse();
        expect(component.maxGrade).toBe('A');
        expect(component.averageGrade).toBe('A');
    });

    it('should set highlighting to default if current highlighting is deselected', () => {
        component.highlightedType = HighlightType.AVERAGE;

        // we deselect the current highlighting
        component.highlightBar(HighlightType.AVERAGE);

        expect(component.highlightedType).toBe(HighlightType.NONE);
        expect(component.valueToHighlight).toBeUndefined();
    });

    it('should highlight the median correctly', () => {
        component.highlightedType = HighlightType.AVERAGE;
        component.medianScoreIncluded = 55.5;

        // we select the median
        component.highlightBar(HighlightType.MEDIAN);

        expect(component.highlightedType).toBe(HighlightType.MEDIAN);
        expect(component.valueToHighlight).toBe(55.5);
    });

    function validateUserExportRow(
        userRow: any,
        expectedName: string,
        expectedUsername: string,
        expectedEmail: string,
        expectedQuizPoints: string | CommonSpreadsheetCellObject,
        expectedQuizScore: string | CommonSpreadsheetCellObject,
        expectedScoreModelingExercise: string | CommonSpreadsheetCellObject,
        expectedModelingPoints: string | CommonSpreadsheetCellObject,
        expectedModelingScore: string | CommonSpreadsheetCellObject,
        expectedTextPoints: string | CommonSpreadsheetCellObject,
        expectedTextScore: string | CommonSpreadsheetCellObject,
        expectedFileUploadPoints: string | CommonSpreadsheetCellObject,
        expectedFileUploadScore: string | CommonSpreadsheetCellObject,
        expectedPresentationPoints: string | CommonSpreadsheetCellObject,
        expectedPresentationScore: string | CommonSpreadsheetCellObject,
        expectedOverallCoursePoints: string | CommonSpreadsheetCellObject,
        expectedOverallCourseScore: string | CommonSpreadsheetCellObject,
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
        expect(userRow[PRESENTATION_POINTS_KEY]).toEqual(expectedPresentationPoints);
        expect(userRow[PRESENTATION_SCORE_KEY]).toEqual(expectedPresentationScore);
        expect(userRow[COURSE_OVERALL_POINTS_KEY]).toEqual(expectedOverallCoursePoints);
        expect(userRow[COURSE_OVERALL_SCORE_KEY]).toEqual(expectedOverallCourseScore);
    }
});

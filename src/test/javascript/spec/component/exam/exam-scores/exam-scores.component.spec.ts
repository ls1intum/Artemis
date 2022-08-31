import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import {
    AggregatedExamResult,
    AggregatedExerciseGroupResult,
    ExamScoreDTO,
    ExerciseGroup,
    ExerciseInfo,
    ExerciseResult,
    StudentResult,
} from 'app/exam/exam-scores/exam-score-dtos.model';
import { ExamScoresComponent, MedianType } from 'app/exam/exam-scores/exam-scores.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ParticipantScoresService, ScoresDTO } from 'app/shared/participant-scores/participant-scores.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { cloneDeep } from 'lodash-es';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { EMPTY, of } from 'rxjs';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { GradingScale } from 'app/entities/grading-scale.model';
import { GradeStep } from 'app/entities/grade-step.model';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/exam-scores/exam-scores-average-scores-graph.component';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { AlertService } from 'app/core/util/alert.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { MockRouterLinkDirective } from '../../../helpers/mocks/directive/mock-router-link.directive';
import { ParticipantScoresDistributionComponent } from 'app/shared/participant-scores/participant-scores-distribution/participant-scores-distribution.component';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings } from 'app/shared/export/export-modal.component';
import {
    EMAIL_KEY,
    EXAM_ACHIEVED_POINTS,
    EXAM_ACHIEVED_SCORE,
    EXAM_ASSIGNED_EXERCISE,
    EXAM_OVERALL_POINTS_KEY,
    EXAM_OVERALL_SCORE_KEY,
    EXAM_SUBMITTED,
    NAME_KEY,
    REGISTRATION_NUMBER_KEY,
    USERNAME_KEY,
} from 'app/shared/export/export-constants';
import { ExportButtonComponent } from 'app/shared/export/export-button.component';

describe('ExamScoresComponent', () => {
    let fixture: ComponentFixture<ExamScoresComponent>;
    let comp: ExamScoresComponent;
    let examService: ExamManagementService;
    let gradingSystemService: GradingSystemService;
    let accountService: AccountService;

    let routingStub: jest.SpyInstance;

    const gradeStep1: GradeStep = {
        isPassingGrade: false,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 0,
        upperBoundInclusive: false,
        upperBoundPercentage: 40,
        gradeName: '4',
    };
    const gradeStep2: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 40,
        upperBoundInclusive: false,
        upperBoundPercentage: 60,
        gradeName: '3',
    };
    const gradeStep3: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 60,
        upperBoundInclusive: false,
        upperBoundPercentage: 80,
        gradeName: '2',
    };
    const gradeStep4: GradeStep = {
        isPassingGrade: true,
        lowerBoundInclusive: true,
        lowerBoundPercentage: 80,
        upperBoundInclusive: true,
        upperBoundPercentage: 100,
        gradeName: '1',
    };
    const gradingScale = new GradingScale();
    gradingScale.gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep4];

    const exInfo1 = {
        exerciseId: 11,
        title: 'ex1_1',
        maxPoints: 100,
        numberOfParticipants: 1,
        exerciseType: 'TextExercise',
    } as ExerciseInfo;
    const exInfo2 = {
        exerciseId: 12,
        title: 'ex1_2',
        maxPoints: 100,
        numberOfParticipants: 1,
        exerciseType: 'ModelingExercise',
    } as ExerciseInfo;
    const exInfo3 = {
        exerciseId: 13,
        title: 'ex1_3',
        maxPoints: 100,
        numberOfParticipants: 1,
        exerciseType: 'ProgrammingExercise',
    } as ExerciseInfo;
    const exInfo4 = {
        exerciseId: 14,
        title: 'ex1_4',
        maxPoints: 100,
        numberOfParticipants: 1,
        exerciseType: 'FileUploadExercise',
    } as ExerciseInfo;
    const exInfo5 = {
        exerciseId: 15,
        title: 'ex1_5',
        maxPoints: 100,
        numberOfParticipants: 1,
        exerciseType: 'QuizExercise',
    } as ExerciseInfo;

    const exGroup1Id = 1;
    const exGroup1 = {
        id: exGroup1Id,
        title: 'group',
        maxPoints: 100,
        numberOfParticipants: 2,
        containedExercises: [exInfo1, exInfo2, exInfo3, exInfo4, exInfo5],
    } as ExerciseGroup;

    const exResult1ForGroup1 = {
        exerciseId: 11,
        title: 'exResult1_1',
        maxScore: 100,
        achievedScore: 100,
        achievedPoints: 100,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const exResult2ForGroup1 = {
        exerciseId: 12,
        title: 'exResult1_2',
        maxScore: 100,
        achievedScore: 20,
        achievedPoints: 20,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const exResult3ForGroup1 = {
        exerciseId: 12,
        title: 'exResult1_2',
        maxScore: 100,
        achievedScore: 50,
        achievedPoints: 50,
        hasNonEmptySubmission: true,
    } as ExerciseResult;

    const studentResult1 = {
        userId: 1,
        name: 'user1',
        login: 'user1',
        email: 'user1@tum.de',
        registrationNumber: '111',
        overallPointsAchieved: 100,
        overallScoreAchieved: 100,
        overallPointsAchievedInFirstCorrection: 90,
        submitted: true,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult1ForGroup1 },
    } as StudentResult;

    const studentResult2 = {
        userId: 2,
        name: 'user2',
        login: 'user2',
        email: 'user2@tum.de',
        registrationNumber: '222',
        overallPointsAchieved: 20,
        overallScoreAchieved: 20,
        overallPointsAchievedInFirstCorrection: 20,
        submitted: true,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult2ForGroup1 },
    } as StudentResult;

    const studentResult3 = {
        userId: 3,
        name: 'user3',
        login: 'user3',
        email: 'user3@tum.de',
        registrationNumber: '333',
        overallPointsAchieved: 50,
        overallScoreAchieved: 50,
        overallPointsAchievedInFirstCorrection: 40,
        submitted: false,
        exerciseGroupIdToExerciseResult: { [exGroup1Id]: exResult3ForGroup1 },
    } as StudentResult;

    let findExamScoresSpy: jest.SpyInstance;

    const examScoreStudent1 = new ScoresDTO();
    examScoreStudent1.studentId = studentResult1.userId;
    examScoreStudent1.pointsAchieved = studentResult1.overallPointsAchieved;
    examScoreStudent1.studentLogin = studentResult1.login;
    examScoreStudent1.scoreAchieved = studentResult1.overallScoreAchieved;
    const examScoreStudent2 = new ScoresDTO();
    examScoreStudent2.studentId = studentResult2.userId;
    examScoreStudent2.pointsAchieved = studentResult2.overallPointsAchieved;
    examScoreStudent2.studentLogin = studentResult2.login;
    examScoreStudent2.scoreAchieved = studentResult2.overallScoreAchieved;
    const examScoreStudent3 = new ScoresDTO();
    examScoreStudent3.studentId = studentResult3.userId;
    examScoreStudent3.pointsAchieved = studentResult3.overallPointsAchieved;
    examScoreStudent3.studentLogin = studentResult3.login;
    examScoreStudent3.scoreAchieved = studentResult3.overallScoreAchieved;
    const examScoreDTO = {
        examId: 1,
        title: 'exam1',
        maxPoints: 100,
        averagePointsAchieved: 60,
        hasSecondCorrectionAndStarted: true,
        exerciseGroups: [exGroup1],
        studentResults: [studentResult1, studentResult2, studentResult3],
    } as ExamScoreDTO;

    global.URL.createObjectURL = jest.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = jest.fn(() => '');

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [
                ExamScoresComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(HelpIconComponent),
                MockComponent(ExportButtonComponent),
                MockDirective(TranslateDirective),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockDirective(DeleteButtonDirective),
                MockComponent(ExamScoresAverageScoresGraphComponent),
                MockRouterLinkDirective,
                MockComponent(ParticipantScoresDistributionComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: { params: of({ courseId: 1, examId: 1 }) } },
                { provide: Router, useClass: MockRouter },
                MockProvider(AccountService),
                MockProvider(ArtemisNavigationUtilService),
                MockProvider(TranslateService),
                MockProvider(ExamManagementService),
                MockProvider(SortService),
                MockProvider(AlertService),
                MockProvider(ParticipantScoresService),
                MockProvider(GradingSystemService, {
                    findGradingScaleForExam: () => {
                        return of(
                            new HttpResponse({
                                body: new GradingScale(),
                                status: 200,
                            }),
                        );
                    },
                    findMatchingGradeStep: () => {
                        return gradeStep1;
                    },
                    sortGradeSteps: () => {
                        return [gradeStep1, gradeStep2, gradeStep3, gradeStep4];
                    },
                }),
                MockProvider(JhiLanguageHelper, { language: EMPTY }),
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(new HttpResponse({ body: { accuracyOfScores: 1 } }));
                    },
                }),
                MockProvider(LocaleConversionService, {
                    toLocaleString: (value: number) => {
                        return isNaN(value) ? '-' : value.toString();
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamScoresComponent);
                comp = fixture.componentInstance;
                examService = fixture.debugElement.injector.get(ExamManagementService);
                gradingSystemService = fixture.debugElement.injector.get(GradingSystemService);
                const participationScoreService = fixture.debugElement.injector.get(ParticipantScoresService);
                findExamScoresSpy = jest
                    .spyOn(participationScoreService, 'findExamScores')
                    .mockReturnValue(of(new HttpResponse({ body: [examScoreStudent1, examScoreStudent2, examScoreStudent3] })));
                accountService = TestBed.inject(AccountService);
                const navigationUtilService = TestBed.inject(ArtemisNavigationUtilService);

                routingStub = jest.spyOn(navigationUtilService, 'routeInNewTab');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should not log error on sentry when correct participant score calculation', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        const errorSpy = jest.spyOn(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('should log error on sentry when missing participant score calculation', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [] })));
        const errorSpy = jest.spyOn(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });
    it('should log error on sentry when wrong points calculation', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = cloneDeep(examScoreStudent1);
        cs1.pointsAchieved = 99;
        const cs2 = cloneDeep(examScoreStudent2);
        cs2.pointsAchieved = 99;
        const cs3 = cloneDeep(examScoreStudent3);
        cs3.pointsAchieved = 99;
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = jest.spyOn(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });

    it('should log error on sentry when wrong score calculation', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = cloneDeep(examScoreStudent1);
        cs1.scoreAchieved = 99;
        const cs2 = cloneDeep(examScoreStudent2);
        cs2.scoreAchieved = 99;
        const cs3 = cloneDeep(examScoreStudent3);
        cs3.scoreAchieved = 99;
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = jest.spyOn(comp, 'logErrorOnSentry');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });

    it('should make duplicated titles unique', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));

        const newExerciseGroup = new ExerciseGroup();
        newExerciseGroup.title = 'group';
        newExerciseGroup.id = 2;
        examScoreDTO.exerciseGroups.push(newExerciseGroup);
        fixture.detectChanges();

        expect(examScoreDTO.exerciseGroups[0].title).toBe(`group (id=1)`);
        expect(examScoreDTO.exerciseGroups[1].title).toBe(`group (id=2)`);

        // reset state
        examScoreDTO.exerciseGroups.pop();
        examScoreDTO.exerciseGroups[0].title = 'group';
    });

    it('histogram should have correct entries', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScale>({ status: 404 })));
        fixture.detectChanges();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        const noOfSubmittedExercises = examScoreDTO.studentResults.length;

        // expect three distinct scores
        expect(comp.scores).toHaveLength(3);
        expect(comp.noOfExamsFiltered).toBe(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults.find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
        const expectedGroupResult = {
            noOfParticipantsWithFilter: 3,
            totalPoints: 170,
            exerciseResults: [
                {
                    noOfParticipantsWithFilter: 1,
                    totalPoints: 100,
                    exerciseId: 11,
                    title: 'ex1_1',
                    maxPoints: 100,
                    totalParticipants: 1,
                    exerciseType: 'text',
                    averagePoints: 100,
                    averagePercentage: 100,
                },
                {
                    noOfParticipantsWithFilter: 2,
                    totalPoints: 70,
                    exerciseId: 12,
                    title: 'ex1_2',
                    maxPoints: 100,
                    totalParticipants: 1,
                    exerciseType: 'modeling',
                    averagePoints: 35,
                    averagePercentage: 35,
                },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 13, title: 'ex1_3', maxPoints: 100, totalParticipants: 1, exerciseType: 'programming' },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 14, title: 'ex1_4', maxPoints: 100, totalParticipants: 1, exerciseType: 'file-upload' },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 15, title: 'ex1_5', maxPoints: 100, totalParticipants: 1, exerciseType: 'quiz' },
            ],
            exerciseGroupId: 1,
            title: 'group',
            maxPoints: 100,
            totalParticipants: 2,
            averagePoints: 56.666666666666664,
            averagePercentage: 56.666666666666664,
        } as AggregatedExerciseGroupResult;

        expect(groupResult1).toEqual(expectedGroupResult);

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints! + exResult3ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).toBe(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).toBe(averagePoints);
        expect(groupResult1!.averagePercentage).toBe((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults).toHaveLength(5);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints = 0;
            let exInfo;
            if (exResult.exerciseId === 11) {
                // result for ex 1_1
                averageExPoints = exResult1ForGroup1.achievedPoints!;
                expect(exResult.averagePoints).toBe(averageExPoints);
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 11)!;
                expect(exResult.averagePercentage).toBe((averageExPoints / exInfo.maxPoints) * 100);
            } else if (exResult.exerciseId === 12) {
                // result for ex 1_2
                averageExPoints = (exResult2ForGroup1.achievedPoints! + exResult3ForGroup1.achievedPoints!) / 2;
                expect(exResult.averagePoints).toBe(averageExPoints);
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 12)!;
                expect(exResult.averagePercentage).toBe((averageExPoints / exInfo.maxPoints) * 100);
            }
        });
    });

    it('histogram should skip not submitted exams', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScale>({ status: 404 })));
        fixture.detectChanges();
        comp.toggleFilterForSubmittedExam();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        // it should skip the not submitted one
        const noOfSubmittedExercises = examScoreDTO.studentResults.length - 1;
        // expect two distinct scores
        expect(comp.scores).toHaveLength(2);
        expect(comp.noOfExamsFiltered).toBe(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults.find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
        const expectedGroupResult = {
            noOfParticipantsWithFilter: 2,
            totalPoints: 120,
            exerciseResults: [
                {
                    noOfParticipantsWithFilter: 1,
                    totalPoints: 100,
                    exerciseId: 11,
                    title: 'ex1_1',
                    maxPoints: 100,
                    totalParticipants: 1,
                    exerciseType: 'text',
                    averagePoints: 100,
                    averagePercentage: 100,
                },
                {
                    noOfParticipantsWithFilter: 1,
                    totalPoints: 20,
                    exerciseId: 12,
                    title: 'ex1_2',
                    maxPoints: 100,
                    totalParticipants: 1,
                    exerciseType: 'modeling',
                    averagePoints: 20,
                    averagePercentage: 20,
                },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 13, title: 'ex1_3', maxPoints: 100, totalParticipants: 1, exerciseType: 'programming' },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 14, title: 'ex1_4', maxPoints: 100, totalParticipants: 1, exerciseType: 'file-upload' },
                { noOfParticipantsWithFilter: 0, totalPoints: 0, exerciseId: 15, title: 'ex1_5', maxPoints: 100, totalParticipants: 1, exerciseType: 'quiz' },
            ],
            exerciseGroupId: 1,
            title: 'group',
            maxPoints: 100,
            totalParticipants: 2,
            averagePoints: 60,
            averagePercentage: 60,
        };
        expect(groupResult1).toEqual(expectedGroupResult);

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).toBe(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).toBe(averagePoints);
        expect(groupResult1!.averagePercentage).toBe((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults).toHaveLength(5);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints = 0;
            let exInfo;
            if (exResult.exerciseId === 11) {
                // result for ex 1_1
                averageExPoints = exResult1ForGroup1.achievedPoints!;
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 11)!;
                expect(exResult.averagePoints).toBe(averageExPoints);
                expect(exResult.averagePercentage).toBe((averageExPoints / exInfo.maxPoints) * 100);
            } else if (exResult.exerciseId === 12) {
                // result for ex 1_2
                averageExPoints = exResult2ForGroup1.achievedPoints!;
                exInfo = exGroup1.containedExercises.find((ex) => ex.exerciseId === 12)!;
                expect(exResult.averagePoints).toBe(averageExPoints);
                expect(exResult.averagePercentage).toBe((averageExPoints / exInfo.maxPoints) * 100);
            }
        });
    });

    it('should generate csv correctly', () => {
        const noOfSubmittedExercises = examScoreDTO.studentResults.length;
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        comp.gradingScale = gradingScale;
        comp.gradingScale.gradeSteps = [gradeStep1];
        comp.gradingScaleExists = true;

        const exportAsCsvStub = jest.spyOn(comp, 'exportAsCsv');
        // create csv
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: CsvQuoteStrings.QUOTES_DOUBLE,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };
        comp.exportExamResults(testOptions);

        const generatedRows = exportAsCsvStub.mock.calls[0][1];
        expect(generatedRows).toHaveLength(noOfSubmittedExercises);
        const user1Row = generatedRows[0];
        validateUserRow(
            user1Row,
            studentResult1.name,
            studentResult1.login,
            studentResult1.email,
            studentResult1.registrationNumber,
            'exResult1_1',
            '100',
            '100%',
            '100',
            '100%',
            studentResult1.submitted ? 'yes' : 'no',
        );
        const user2Row = generatedRows[1];
        validateUserRow(
            user2Row,
            studentResult2.name,
            studentResult2.login,
            studentResult2.email,
            studentResult2.registrationNumber,
            'exResult1_2',
            '20',
            '20%',
            '20',
            '20%',
            studentResult2.submitted ? 'yes' : 'no',
        );
        const user3Row = generatedRows[2];
        validateUserRow(
            user3Row,
            studentResult3.name,
            studentResult3.login,
            studentResult3.email,
            studentResult3.registrationNumber,
            'exResult1_2',
            '50',
            '50%',
            '50',
            '50%',
            studentResult3.submitted ? 'yes' : 'no',
        );
    });

    it('should export as csv', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();

        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteStrings: CsvQuoteStrings.QUOTES_DOUBLE,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };
        comp.exportExamResults(testOptions);
    });

    it('should set grading scale properties', () => {
        const examScoreDTOWithGrades = examScoreDTO;
        examScoreDTOWithGrades.studentResults[0].hasPassed = true;
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOWithGrades })));
        jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse({ body: gradingScale })));
        jest.spyOn(gradingSystemService, 'findMatchingGradeStep').mockReturnValue(gradingScale.gradeSteps[0]);
        fixture.detectChanges();

        expect(comp.gradingScaleExists).toBeTrue();
        expect(comp.gradingScale).toEqual(gradingScale);
        expect(comp.isBonus).toBeFalse();
    });

    it('should filter non-empty submissions', () => {
        comp.filterForNonEmptySubmissions = false;
        comp.gradingScale = gradingScale;
        comp.gradingScale.gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep4];
        comp.gradingScaleExists = true;
        comp.exerciseGroups = examScoreDTO.exerciseGroups;
        comp.studentResults = examScoreDTO.studentResults;
        comp.examScoreDTO = examScoreDTO;
        comp.aggregatedExamResults = new AggregatedExamResult();
        comp.course = { accuracyOfScores: 1 };
        jest.spyOn(gradingSystemService, 'findMatchingGradeStep').mockReturnValue(gradingScale.gradeSteps[0]);

        comp.toggleFilterForNonEmptySubmission();

        expect(comp.filterForNonEmptySubmissions).toBeTrue();
    });

    describe('test table filtering', () => {
        const examScoreDTOOnePassing = examScoreDTO;
        examScoreDTOOnePassing.studentResults[0].hasPassed = true;
        it('should set table state correctly if non empty submissions filter is activated', () => {
            jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScale>({ body: gradingScale })));
            comp.filterForNonEmptySubmissions = false;
            comp.ngOnInit();

            comp.toggleFilterForNonEmptySubmission();

            expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
            expect(comp.tableState.relativeAmountOfSubmittedExams).toBe('66.7');
            expect(comp.tableState.absoluteAmountOfTotalExams).toBe(3);
            expect(comp.tableState.relativeAmountOfPassedExams).toBe('33.3');
            expect(comp.tableState.averagePointsSubmitted).toBe('60');
            expect(comp.tableState.averagePointsTotal).toBe('56.7');
            expect(comp.tableState.averageScoreSubmitted).toBe('60');
            expect(comp.tableState.averageScoreTotal).toBe('56.7');
            expect(comp.tableState.medianPointsSubmitted).toBe('60');
            expect(comp.tableState.medianPointsTotal).toBe('50');
            expect(comp.tableState.medianScoreSubmitted).toBe('60');
            expect(comp.tableState.medianScoreTotal).toBe('50');
            expect(comp.tableState.standardDeviationSubmitted).toBe('40');
            expect(comp.tableState.standardDeviationTotal).toBe('33');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.medianGradeSubmitted).toBe('4');
            expect(comp.tableState.medianGradeTotal).toBe('4');
            expect(comp.tableState.averagePointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.averagePointsTotalInFirstCorrection).toBe('50');
            expect(comp.tableState.averageScoreSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.averageScoreTotalInFirstCorrection).toBe('50');
            expect(comp.tableState.medianPointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianPointsTotalInFirstCorrection).toBe('40');
            expect(comp.tableState.medianScoreSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianScoreTotalInFirstCorrection).toBe('40');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.standardDeviationSubmittedInFirstCorrection).toBe('35');
            expect(comp.tableState.standardDeviationTotalInFirstCorrection).toBe('29.4');
            expect(comp.tableState.standardGradeDeviationSubmittedInFirstCorrection).toBe('-');
            expect(comp.tableState.standardGradeDeviationTotalInFirstCorrection).toBe('-');
            expect(comp.tableState.averageGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.averageGradeTotalInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeTotalInFirstCorrection).toBe('4');
        });

        it('should set table state correctly if only submitted exams filter is activated', () => {
            jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScale>({ body: gradingScale })));
            comp.filterForSubmittedExams = false;
            comp.ngOnInit();

            comp.toggleFilterForSubmittedExam();

            expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
            expect(comp.tableState.relativeAmountOfSubmittedExams).toBe('100');
            expect(comp.tableState.absoluteAmountOfTotalExams).toBe(2);
            expect(comp.tableState.relativeAmountOfPassedExams).toBe('50');
            expect(comp.tableState.averagePointsSubmitted).toBe('60');
            expect(comp.tableState.averagePointsTotal).toBe('60');
            expect(comp.tableState.averageScoreSubmitted).toBe('60');
            expect(comp.tableState.averageScoreTotal).toBe('60');
            expect(comp.tableState.medianPointsSubmitted).toBe('60');
            expect(comp.tableState.medianPointsTotal).toBe('60');
            expect(comp.tableState.medianScoreSubmitted).toBe('60');
            expect(comp.tableState.medianScoreTotal).toBe('60');
            expect(comp.tableState.standardDeviationSubmitted).toBe('40');
            expect(comp.tableState.standardDeviationTotal).toBe('40');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.medianGradeSubmitted).toBe('4');
            expect(comp.tableState.medianGradeTotal).toBe('4');
            expect(comp.tableState.averagePointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.averagePointsTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.averageScoreSubmittedInFirstCorrection).toBe('60');
            expect(comp.tableState.averageScoreTotalInFirstCorrection).toBe('60');
            expect(comp.tableState.medianPointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianPointsTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.medianScoreSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianScoreTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.standardDeviationSubmittedInFirstCorrection).toBe('35');
            expect(comp.tableState.standardDeviationTotalInFirstCorrection).toBe('35');
            expect(comp.tableState.standardGradeDeviationSubmittedInFirstCorrection).toBe('-');
            expect(comp.tableState.standardGradeDeviationTotalInFirstCorrection).toBe('-');
            expect(comp.tableState.averageGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.averageGradeTotalInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeTotalInFirstCorrection).toBe('4');
        });

        it('should set table state correctly if both exams are activated', () => {
            jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            jest.spyOn(gradingSystemService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScale>({ body: gradingScale })));
            comp.filterForNonEmptySubmissions = false;
            comp.filterForSubmittedExams = false;
            comp.ngOnInit();

            comp.toggleFilterForSubmittedExam();
            comp.toggleFilterForNonEmptySubmission();

            expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
            expect(comp.tableState.relativeAmountOfSubmittedExams).toBe('100');
            expect(comp.tableState.absoluteAmountOfTotalExams).toBe(2);
            expect(comp.tableState.relativeAmountOfPassedExams).toBe('50');
            expect(comp.tableState.averagePointsSubmitted).toBe('60');
            expect(comp.tableState.averagePointsTotal).toBe('60');
            expect(comp.tableState.averageScoreSubmitted).toBe('60');
            expect(comp.tableState.averageScoreTotal).toBe('60');
            expect(comp.tableState.medianPointsSubmitted).toBe('60');
            expect(comp.tableState.medianPointsTotal).toBe('60');
            expect(comp.tableState.medianScoreSubmitted).toBe('60');
            expect(comp.tableState.medianScoreTotal).toBe('60');
            expect(comp.tableState.standardDeviationSubmitted).toBe('40');
            expect(comp.tableState.standardDeviationTotal).toBe('40');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.medianGradeSubmitted).toBe('4');
            expect(comp.tableState.medianGradeTotal).toBe('4');
            expect(comp.tableState.averagePointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.averagePointsTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.averageScoreSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.averageScoreTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.medianPointsSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianPointsTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.medianScoreSubmittedInFirstCorrection).toBe('55');
            expect(comp.tableState.medianScoreTotalInFirstCorrection).toBe('55');
            expect(comp.tableState.averageGradeSubmitted).toBe('4');
            expect(comp.tableState.averageGradeTotal).toBe('4');
            expect(comp.tableState.standardDeviationSubmittedInFirstCorrection).toBe('35');
            expect(comp.tableState.standardDeviationTotalInFirstCorrection).toBe('35');
            expect(comp.tableState.standardGradeDeviationSubmittedInFirstCorrection).toBe('-');
            expect(comp.tableState.standardGradeDeviationTotalInFirstCorrection).toBe('-');
            expect(comp.tableState.averageGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.averageGradeTotalInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeSubmittedInFirstCorrection).toBe('4');
            expect(comp.tableState.medianGradeTotalInFirstCorrection).toBe('4');
        });
    });

    it('should not delegate user if authorisation is not sufficient', () => {
        jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(false);

        comp.onSelect();

        expect(routingStub).not.toHaveBeenCalled();
    });

    it('should delegate user if authorisation is sufficient', () => {
        jest.spyOn(accountService, 'hasAnyAuthorityDirect').mockReturnValue(true);
        comp.course = { id: 42 } as Course;
        comp.examScoreDTO = examScoreDTO;

        comp.onSelect();

        expect(routingStub).toHaveBeenCalledWith(['course-management', 42, 'exams', 1, 'participant-scores']);
    });

    it('should toggle median correctly', () => {
        jest.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        comp.isBonus = false;
        fixture.detectChanges();

        expect(comp.showPassedMedian).toBeTrue();

        comp.toggleMedian(MedianType.PASSED);

        expect(comp.showPassedMedian).toBeFalse();

        comp.toggleMedian(MedianType.OVERALL);

        expect(comp.overallChartMedian).toBe(50);
        expect(comp.showOverallMedian).toBeTrue();

        comp.toggleMedian(MedianType.PASSED);

        expect(comp.showPassedMedian).toBeTrue();
        expect(comp.showOverallMedian).toBeFalse();
    });

    it('should return data label correctly if noOfExamsFiltered is 0', () => {
        comp.noOfExamsFiltered = 0;

        const dataLabel = comp.formatDataLabel(0);

        expect(dataLabel).toBe('0 (0%)');
    });
});

function expectCorrectExamScoreDto(comp: ExamScoresComponent, examScoreDTO: ExamScoreDTO) {
    expect(comp.examScoreDTO).toEqual(examScoreDTO);
    expect(comp.studentResults).toEqual(examScoreDTO.studentResults);
    expect(comp.exerciseGroups).toEqual(examScoreDTO.exerciseGroups);
}

function validateUserRow(
    userRow: any,
    expectedName: string,
    expectedUsername: string,
    expectedEmail: string,
    expectedRegistrationNumber: string,
    expectedExerciseTitle: string,
    expectedAchievedPoints: string,
    expectedAchievedScore: string,
    expectedOverAllPoints: string,
    expectedOverAllScore: string,
    expectedSubmitted: string,
) {
    expect(userRow[NAME_KEY]).toBe(expectedName);
    expect(userRow[USERNAME_KEY]).toBe(expectedUsername);
    expect(userRow[EMAIL_KEY]).toBe(expectedEmail);
    expect(userRow[REGISTRATION_NUMBER_KEY]).toBe(expectedRegistrationNumber);
    expect(userRow[`group ${EXAM_ASSIGNED_EXERCISE}`]).toBe(expectedExerciseTitle);
    expect(userRow[`group ${EXAM_ACHIEVED_POINTS}`]).toBe(expectedAchievedPoints);
    expect(userRow[`group ${EXAM_ACHIEVED_SCORE}`]).toBe(expectedAchievedScore);
    expect(userRow[EXAM_OVERALL_POINTS_KEY]).toBe(expectedOverAllPoints);
    expect(userRow[EXAM_OVERALL_SCORE_KEY]).toBe(expectedOverAllScore);
    expect(userRow[EXAM_SUBMITTED]).toBe(expectedSubmitted);
}

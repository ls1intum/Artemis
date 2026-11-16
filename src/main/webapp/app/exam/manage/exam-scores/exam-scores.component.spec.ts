import * as XLSX from '@e965/xlsx';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import {
    AggregatedExamResult,
    AggregatedExerciseGroupResult,
    ExamScoreDTO,
    ExerciseGroup,
    ExerciseInfo,
    ExerciseResult,
    StudentResult,
} from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ExamScoresComponent, MedianType } from 'app/exam/manage/exam-scores/exam-scores.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ParticipantScoresService, ScoresDTO } from 'app/course/participant-scores/participant-scores.service';
import { EMPTY, of } from 'rxjs';
import { ExportButtonComponent } from 'app/shared-ui/export/button/export-button.component';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { CsvDecimalSeparator, CsvExportOptions, CsvFieldSeparator, CsvQuoteStrings } from 'app/shared-ui/export/modal/export-modal.component';
import {
    BONUS_GRADE_KEY,
    EMAIL_KEY,
    EXAM_ACHIEVED_POINTS,
    EXAM_ACHIEVED_SCORE,
    EXAM_ASSIGNED_EXERCISE,
    EXAM_OVERALL_POINTS_KEY,
    EXAM_OVERALL_SCORE_KEY,
    EXAM_PASSED,
    EXAM_SUBMITTED,
    FINAL_GRADE_KEY,
    NAME_KEY,
    PLAGIARISM_VERDICT_IN_BONUS_SOURCE_KEY,
    PLAGIARISM_VERDICT_KEY,
    REGISTRATION_NUMBER_KEY,
    USERNAME_KEY,
} from 'app/shared-ui/export/export-constants';
import { PlagiarismVerdict } from 'app/plagiarism/shared/entities/PlagiarismVerdict';
import { BonusStrategy } from 'app/assessment/shared/entities/bonus.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { GradingScaleDTO, toGradingScaleDTO } from 'app/assessment/shared/entities/grading-scale-dto.model';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@e965/xlsx', async (importOriginal) => {
    const actual = await importOriginal<typeof import('@e965/xlsx')>();
    return { ...actual, writeFile: vi.fn() };
});
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';

describe('ExamScoresComponent', () => {
    let fixture: ComponentFixture<ExamScoresComponent>;
    let comp: ExamScoresComponent;
    let examService: ExamManagementService;
    let gradingService: GradingService;

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
    gradingScale.plagiarismGrade = 'U';
    gradingScale.noParticipationGrade = 'X';
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

    const studentResultsWithBonusGradesAndPlagiarism = [
        {
            ...studentResult1,
            gradeWithBonus: { bonusGrade: 1, finalGrade: '2.0', bonusStrategy: BonusStrategy.GRADES_DISCRETE },
            mostSeverePlagiarismVerdict: PlagiarismVerdict.WARNING,
        },
        { ...studentResult2, gradeWithBonus: { bonusGrade: 2, finalGrade: '1.0', bonusStrategy: BonusStrategy.GRADES_DISCRETE } },
        {
            ...studentResult3,
            gradeWithBonus: {
                bonusGrade: 2,
                finalGrade: '5.0',
                bonusStrategy: BonusStrategy.GRADES_DISCRETE,
                mostSeverePlagiarismVerdict: PlagiarismVerdict.WARNING,
            },
        },
    ];

    const studentResultsWithBonusPoints = [
        {
            ...studentResult1,
            gradeWithBonus: { bonusGrade: 20, finalGrade: '2.0', bonusStrategy: BonusStrategy.POINTS },
        },
        {
            ...studentResult2,
            gradeWithBonus: { bonusGrade: 40, finalGrade: '1.0', bonusStrategy: BonusStrategy.POINTS },
        },
        {
            ...studentResult3,
            gradeWithBonus: { bonusGrade: 40, finalGrade: '5.0', bonusStrategy: BonusStrategy.POINTS },
        },
    ];

    let findExamScoresSpy: ReturnType<typeof vi.spyOn>;

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

    global.URL.createObjectURL = vi.fn(() => 'http://some.test.com');
    global.URL.revokeObjectURL = vi.fn(() => '');

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockProvider(GradingService, {
                    findGradingScaleForExam: () => {
                        return of(
                            new HttpResponse({
                                body: toGradingScaleDTO(new GradingScale()),
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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 123 }) },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            // The tests call the export methods directly, so the export button, which opens a dialog, is not rendered.
            .overrideComponent(ExamScoresComponent, { remove: { imports: [ExportButtonComponent] }, add: { imports: [MockComponent(ExportButtonComponent)] } })
            .compileComponents();

        fixture = TestBed.createComponent(ExamScoresComponent);
        comp = fixture.componentInstance;
        examService = TestBed.inject(ExamManagementService);
        gradingService = TestBed.inject(GradingService);
        const participationScoreService = TestBed.inject(ParticipantScoresService);
        findExamScoresSpy = vi
            .spyOn(participationScoreService, 'findExamScores')
            .mockReturnValue(of(new HttpResponse({ body: [examScoreStudent1, examScoreStudent2, examScoreStudent3] })));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should not report an error when correct participant score calculation', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        const errorSpy = vi.spyOn(comp, 'reportScoreCalculationError');
        fixture.detectChanges();
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('should report an error when missing participant score calculation', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [] })));
        const errorSpy = vi.spyOn(comp, 'reportScoreCalculationError');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });

    it('should report an error when wrong points calculation', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = deepClone(examScoreStudent1);
        cs1.pointsAchieved = 99;
        const cs2 = deepClone(examScoreStudent2);
        cs2.pointsAchieved = 99;
        const cs3 = deepClone(examScoreStudent3);
        cs3.pointsAchieved = 99;
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = vi.spyOn(comp, 'reportScoreCalculationError');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });

    it('should report an error when wrong score calculation', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        const cs1 = deepClone(examScoreStudent1);
        cs1.scoreAchieved = 99;
        const cs2 = deepClone(examScoreStudent2);
        cs2.scoreAchieved = 99;
        const cs3 = deepClone(examScoreStudent3);
        cs3.scoreAchieved = 99;
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [cs1, cs2, cs3] })));
        const errorSpy = vi.spyOn(comp, 'reportScoreCalculationError');
        fixture.detectChanges();
        expect(errorSpy).toHaveBeenCalledTimes(3);
    });

    it('should locate a discrepancy by the id of the student and report neither the login nor the scores', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        const wrongStudent1 = deepClone(examScoreStudent1);
        wrongStudent1.pointsAchieved = 99;
        wrongStudent1.scoreAchieved = 99;
        findExamScoresSpy.mockReturnValue(of(new HttpResponse({ body: [wrongStudent1, examScoreStudent2] })));
        const errorSpy = vi.spyOn(comp, 'reportScoreCalculationError');

        fixture.detectChanges();

        const messages = errorSpy.mock.calls.map(([message]) => message);
        // the points and the score of student 1 differ, and student 3 is missing
        expect(messages).toEqual([
            'Different exam points of the student with the ID 1 in the new calculation',
            'Different exam score of the student with the ID 1 in the new calculation',
            'Exam scores of the student with the ID 3 are not included in the new calculation',
        ]);
        for (const message of messages) {
            expect(message).not.toMatch(/user[123]/);
            expect(message).not.toMatch(/Achieved|login/i);
            expect(message).not.toMatch(/\b(20|50|99|100)\b/);
        }
    });

    it('should make duplicated titles unique', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));

        const newExerciseGroup = new ExerciseGroup();
        newExerciseGroup.title = 'group';
        newExerciseGroup.id = 2;
        (newExerciseGroup as unknown as { containedExercises: never[] }).containedExercises = [];
        examScoreDTO.exerciseGroups.push(newExerciseGroup);
        fixture.detectChanges();

        expect(examScoreDTO.exerciseGroups[0].title).toBe(`group (id=1)`);
        expect(examScoreDTO.exerciseGroups[1].title).toBe(`group (id=2)`);

        // reset state
        examScoreDTO.exerciseGroups.pop();
        examScoreDTO.exerciseGroups[0].title = 'group';
    });

    it('should sort the student results by the column chosen in the table header', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: structuredClone(examScoreDTO) })));
        vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ status: 404 })));
        fixture.detectChanges();

        comp.onSortChange({ field: 'login', order: 1 });
        const ascendingLogins = comp.studentResults().map((studentResult) => studentResult.login);
        expect(comp.predicate()).toBe('login');
        expect(comp.ascending()).toBe(true);
        expect(ascendingLogins).toEqual([...ascendingLogins].sort());

        comp.onSortChange({ field: 'login', order: -1 });
        expect(comp.ascending()).toBe(false);
        expect(comp.studentResults().map((studentResult) => studentResult.login)).toEqual([...ascendingLogins].reverse());
    });

    it('histogram should have correct entries', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ status: 404 })));
        fixture.detectChanges();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        const noOfSubmittedExercises = examScoreDTO.studentResults.length;

        // expect three distinct scores
        expect(comp.scores()).toHaveLength(3);
        expect(comp.noOfExamsFiltered).toBe(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults().find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
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

        expect(groupResult1).toMatchObject(expectedGroupResult);

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints! + exResult3ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).toBe(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).toBe(averagePoints);
        expect(groupResult1!.averagePercentage).toBe((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults).toHaveLength(5);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints: number;
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
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ status: 404 })));
        fixture.detectChanges();
        comp.toggleFilterForSubmittedExam();

        expectCorrectExamScoreDto(comp, examScoreDTO);

        // it should skip the not submitted one
        const noOfSubmittedExercises = examScoreDTO.studentResults.length - 1;
        // expect two distinct scores
        expect(comp.scores()).toHaveLength(2);
        expect(comp.noOfExamsFiltered).toBe(noOfSubmittedExercises);

        // expect correct calculated exercise group statistics
        const groupResult1 = comp.aggregatedExerciseGroupResults().find((groupRes) => groupRes.exerciseGroupId === exGroup1.id);
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
        expect(groupResult1).toMatchObject(expectedGroupResult);

        const totalPoints = exResult1ForGroup1.achievedPoints! + exResult2ForGroup1.achievedPoints!;
        expect(groupResult1!.totalPoints).toBe(totalPoints);
        const averagePoints = totalPoints / noOfSubmittedExercises;
        expect(groupResult1!.averagePoints).toBe(averagePoints);
        expect(groupResult1!.averagePercentage).toBe((averagePoints / groupResult1!.maxPoints) * 100);

        // expect correct average points for exercises
        expect(groupResult1!.exerciseResults).toHaveLength(5);
        groupResult1!.exerciseResults.forEach((exResult) => {
            let averageExPoints: number;
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

    it('computes score statistics from each scored attempt and excludes pending results', () => {
        const dto = deepClone(examScoreDTO);
        dto.hasSecondCorrectionAndStarted = false;
        dto.studentResults[0].maxPoints = 10.5;
        dto.studentResults[0].overallPointsAchieved = 8.4;
        dto.studentResults[0].overallScoreAchieved = 80;
        dto.studentResults[0].hasPassed = true;
        dto.studentResults[1].maxPoints = 20;
        dto.studentResults[1].overallPointsAchieved = 4;
        dto.studentResults[1].overallScoreAchieved = 20;
        dto.studentResults[2].maxPoints = 15;
        dto.studentResults[2].overallPointsAchieved = undefined;
        dto.studentResults[2].overallScoreAchieved = undefined;
        dto.studentResults[2].submitted = true;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        expect(comp.aggregatedExamResults().meanPointsTotal).toBe(6.2);
        expect(comp.aggregatedExamResults().meanPointsRelativeTotal).toBe(50);
        expect(comp.aggregatedExamResults().medianRelativeTotal).toBe(50);
        expect(comp.aggregatedExamResults().meanPointsRelativeSubmitted).toBe(50);
        expect(comp.aggregatedExamResults().medianRelativeSubmitted).toBe(50);
        expect(comp.aggregatedExamResults().meanPointsRelativePassed).toBe(80);
        expect(comp.scores()).toEqual([80, 20]);
        expect(comp.aggregatedExamResults().noOfRegisteredUsers).toBe(dto.studentResults.length);
        expect(comp.aggregatedExamResults().noOfExamsSubmitted).toBe(dto.studentResults.filter((result) => result.submitted).length);
        expect(Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('[data-testid="attempt-max-points"]')).map((cell) => cell.textContent?.trim())).toEqual([
            '10.5',
            '20',
            '15',
        ]);
        expect(fixture.nativeElement.querySelector('[data-testid="configured-exam-max-points"]')).toBeNull();
        comp.toggleFilterForNonEmptySubmission();
        expect(comp.aggregatedExamResults().meanScoreNonEmpty).toBe(50);
        expect(comp.aggregatedExamResults().medianScoreNonEmpty).toBe(50);
        comp.toggleFilterForSubmittedExam();
        expect(comp.aggregatedExamResults().meanScoreSubmittedAndNonEmpty).toBe(50);
        expect(comp.aggregatedExamResults().medianScoreSubmittedAndNonEmpty).toBe(50);
    });

    it('exports the exact denominator of each test-exam attempt', () => {
        const dto = deepClone(examScoreDTO);
        dto.studentResults[0].maxPoints = 10.5;
        dto.studentResults[0].overallPointsAchieved = 8.4;
        dto.studentResults[0].overallScoreAchieved = 80;
        dto.studentResults[1].maxPoints = 20;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        const exported = vi.spyOn(comp, 'exportAsCsv');
        comp.exportExamResults({
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        });
        expect(exported.mock.calls[0][0]).toContain('Maximum Exam Points');
        expect(exported.mock.calls[0][1][0]['Maximum Exam Points']).toBe('10.5');
        expect(exported.mock.calls[0][1][1]['Maximum Exam Points']).toBe('20');
        expect(exported.mock.calls[0][1][0]['Overall Exam Points']).toBe('8.4');
        expect(exported.mock.calls[0][1][0]['Overall Exam Score']).toBe('80%');
    });

    it.each([true, false])('exports unscored pass values separately from a genuine zero (test exam: %s)', async (testExam) => {
        const dto = deepClone(examScoreDTO);
        dto.studentResults.forEach((result, index) => {
            result.maxPoints = testExam ? (index === 0 ? 0 : 10) : undefined;
            result.overallPointsAchieved = index === 1 ? undefined : 0;
            result.overallScoreAchieved = index === 1 ? undefined : 0;
            result.hasPassed = false;
            result.submitted = true;
            result.overallGrade = index === 2 ? '5' : undefined;
        });
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        comp.gradingScaleExists.set(true);
        comp.isBonus.set(false);
        const options = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };

        comp.exportExamResults(options);

        const blob = vi.mocked(URL.createObjectURL).mock.calls.at(-1)![0] as Blob;
        const workbook = XLSX.read(await blob.text(), { type: 'string', FS: ';' });
        const csvRows = XLSX.utils.sheet_to_json<string[]>(workbook.Sheets[workbook.SheetNames[0]], { header: 1, defval: '' });
        const passColumn = csvRows[0].indexOf(EXAM_PASSED);
        expect(passColumn).toBeGreaterThanOrEqual(0);
        const expected = testExam ? ['', '', 'no'] : ['no', 'no', 'no'];
        expect(csvRows.slice(1).map((row) => row[passColumn])).toEqual(expected);

        const writeFile = vi.mocked(XLSX.writeFile);
        writeFile.mockClear();
        comp.exportExamResults();
        const excelWorkbook = writeFile.mock.calls[0][0];
        const excelRows = XLSX.utils.sheet_to_json<string[]>(excelWorkbook.Sheets[excelWorkbook.SheetNames[0]], { header: 1, defval: '' });
        expect(excelRows.slice(1).map((row) => row[excelRows[0].indexOf(EXAM_PASSED)])).toEqual(expected);
    });

    it('counts pending and manual-only attempts independently of scored statistics in every submission filter', () => {
        const dto = deepClone(examScoreDTO);
        dto.hasSecondCorrectionAndStarted = false;
        dto.studentResults[0].maxPoints = 10;
        dto.studentResults[0].overallPointsAchieved = 0;
        dto.studentResults[0].overallScoreAchieved = 0;
        dto.studentResults[1].maxPoints = 10;
        dto.studentResults[1].overallPointsAchieved = undefined;
        dto.studentResults[1].overallScoreAchieved = undefined;
        dto.studentResults[2].maxPoints = 0;
        dto.studentResults[2].overallPointsAchieved = 0;
        dto.studentResults[2].overallScoreAchieved = 0;
        for (const result of dto.studentResults) {
            for (const exerciseResult of Object.values(result.exerciseGroupIdToExerciseResult ?? {})) {
                exerciseResult.hasNonEmptySubmission = true;
            }
        }
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        expect(comp.aggregatedExamResults().noOfRegisteredUsers).toBe(3);
        expect(comp.aggregatedExamResults().noOfExamsSubmitted).toBe(2);
        expect(comp.aggregatedExamResults().noOfExamsNonEmpty).toBe(3);
        expect(comp.aggregatedExamResults().noOfExamsSubmittedAndNotEmpty).toBe(2);
        expect(Number(comp.tableState.relativeAmountOfSubmittedExams)).toBeCloseTo(66.67, 0);
        expect(comp.aggregatedExamResults().meanPointsTotal).toBe(0);
        expect(comp.scores()).toEqual([0]);
        comp.toggleFilterForSubmittedExam();
        expect(comp.tableState.absoluteAmountOfTotalExams).toBe(2);
        expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
        expect(comp.tableState.relativeAmountOfSubmittedExams).toBe('100');
        comp.toggleFilterForNonEmptySubmission();
        expect(comp.tableState.absoluteAmountOfTotalExams).toBe(2);
        expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
        comp.toggleFilterForSubmittedExam();
        expect(comp.tableState.absoluteAmountOfTotalExams).toBe(3);
        expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
        expect(Number(comp.tableState.relativeAmountOfSubmittedExams)).toBeCloseTo(66.67, 0);
    });

    it.each([undefined, null, Number.NaN])('keeps nonempty unscored exercise metadata out of numeric averages (%s)', (missingScore) => {
        const dto = deepClone(examScoreDTO);
        dto.hasSecondCorrectionAndStarted = false;
        dto.exerciseGroups = [
            {
                id: 1,
                title: 'Scored quiz',
                maxPoints: 10,
                numberOfParticipants: 3,
                containedExercises: [{ exerciseId: 11, title: 'Quiz', maxPoints: 10, numberOfParticipants: 3, exerciseType: 'QuizExercise' }],
            },
            {
                id: 2,
                title: 'Manual essay',
                maxPoints: 20,
                numberOfParticipants: 2,
                containedExercises: [{ exerciseId: 12, title: 'Essay', maxPoints: 20, numberOfParticipants: 2, exerciseType: 'TextExercise' }],
            },
            {
                id: 3,
                title: 'Pending programming',
                maxPoints: 20,
                numberOfParticipants: 1,
                containedExercises: [{ exerciseId: 13, title: 'Programming', maxPoints: 20, numberOfParticipants: 1, exerciseType: 'ProgrammingExercise' }],
            },
        ];
        dto.studentResults.forEach((result, index) => {
            result.submitted = true;
            result.maxPoints = index === 2 ? 20 : 10;
            result.overallPointsAchieved = index === 2 ? (missingScore === undefined ? undefined : 0) : index === 0 ? 4 : 0;
            result.overallScoreAchieved = index === 2 ? (missingScore as number | undefined) : index === 0 ? 40 : 0;
            result.overallPointsAchievedInFirstCorrection = result.overallPointsAchieved;
            result.overallGrade = index === 0 ? '3' : '4';
            result.overallGradeInFirstCorrection = result.overallGrade;
            result.hasPassed = index === 0;
            // JSON omits nullable achieved fields on actual manual and pending server metadata.
            result.exerciseGroupIdToExerciseResult =
                index === 2
                    ? {
                          1: { exerciseId: 11, title: 'Quiz', maxScore: 10, hasNonEmptySubmission: true },
                          3: { exerciseId: 13, title: 'Programming', maxScore: 20, hasNonEmptySubmission: true },
                      }
                    : {
                          1: { exerciseId: 11, title: 'Quiz', maxScore: 10, achievedPoints: index === 0 ? 4 : 0, achievedScore: index === 0 ? 40 : 0, hasNonEmptySubmission: true },
                          2: {
                              exerciseId: 12,
                              title: 'Essay',
                              maxScore: 20,
                              achievedPoints: missingScore as number | undefined,
                              achievedScore: missingScore as number | undefined,
                              hasNonEmptySubmission: true,
                          },
                      };
        });
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        const realGradingService = TestBed.runInInjectionContext(() => new GradingService());
        vi.spyOn(gradingService, 'findMatchingGradeStep').mockImplementation((steps, percentage) => realGradingService.findMatchingGradeStep(steps, percentage));
        fixture.detectChanges();

        const [quiz, manual, pending] = comp.aggregatedExerciseGroupResults();
        expect(quiz.noOfParticipantsWithFilter).toBe(3);
        expect(quiz.noOfScoredParticipantsWithFilter).toBe(2);
        expect(quiz.averagePoints).toBe(2);
        expect(quiz.averagePercentage).toBe(20);
        expect(quiz.exerciseResults[0].averagePoints).toBe(2);
        expect(manual.noOfParticipantsWithFilter).toBe(2);
        expect(manual.noOfScoredParticipantsWithFilter).toBe(0);
        expect(manual.averagePoints).toBeUndefined();
        expect(manual.averagePercentage).toBeUndefined();
        expect(manual.exerciseResults[0].noOfParticipantsWithFilter).toBe(2);
        expect(manual.exerciseResults[0].averagePoints).toBeUndefined();
        expect(pending.noOfParticipantsWithFilter).toBe(1);
        expect(pending.noOfScoredParticipantsWithFilter).toBe(0);
        expect(pending.averagePoints).toBeUndefined();
        expect(pending.exerciseResults[0].noOfParticipantsWithFilter).toBe(1);
        expect(comp.aggregatedExamResults().noOfExamsNonEmpty).toBe(3);
        expect(comp.scores()).toEqual([40, 0]);
        expect(fixture.nativeElement.textContent).not.toContain('NaN');
        const essayCells = Array.from(fixture.nativeElement.querySelectorAll('td') as NodeListOf<HTMLTableCellElement>)
            .map((cell) => cell.textContent?.replace(/\s+/g, ' ').trim())
            .filter((text) => text?.startsWith('Essay :'));
        expect(essayCells).toEqual(['Essay : - ( - %)', 'Essay : - ( - %)']);
        const manualStatistics = Array.from(fixture.nativeElement.querySelectorAll('tr') as NodeListOf<HTMLTableRowElement>).find(
            (row) => row.cells[1]?.textContent?.trim() === 'Manual essay',
        );
        expect(manualStatistics?.cells[4].textContent?.trim()).toBe('-');
    });

    it('refreshes rendered chart eligibility after filtering and keeps first-render exercise navigation valid', async () => {
        const dto = deepClone(examScoreDTO);
        dto.hasSecondCorrectionAndStarted = false;
        dto.exerciseGroups = [
            {
                id: 1,
                title: 'Quiz group',
                maxPoints: 10,
                numberOfParticipants: 1,
                containedExercises: [{ exerciseId: 11, title: 'Quiz', maxPoints: 10, numberOfParticipants: 1, exerciseType: 'QuizExercise' }],
            },
            {
                id: 2,
                title: 'Pending group',
                maxPoints: 20,
                numberOfParticipants: 1,
                containedExercises: [{ exerciseId: 13, title: 'Programming', maxPoints: 20, numberOfParticipants: 1, exerciseType: 'ProgrammingExercise' }],
            },
        ];
        dto.studentResults = dto.studentResults.slice(0, 2);
        dto.studentResults.forEach((result, index) => {
            result.submitted = true;
            result.maxPoints = index === 0 ? 10 : 20;
            result.overallPointsAchieved = index === 0 ? 0 : undefined;
            result.overallScoreAchieved = index === 0 ? 0 : undefined;
            result.exerciseGroupIdToExerciseResult =
                index === 0
                    ? {
                          1: { exerciseId: 11, title: 'Quiz', maxScore: 10, achievedPoints: 0, achievedScore: 0, hasNonEmptySubmission: false },
                      }
                    : {
                          2: { exerciseId: 13, title: 'Programming', maxScore: 20, hasNonEmptySubmission: true },
                      };
        });
        vi.spyOn(Element.prototype, 'getBoundingClientRect').mockReturnValue({
            width: 600,
            height: 300,
            top: 0,
            left: 0,
            right: 600,
            bottom: 300,
            x: 0,
            y: 0,
            toJSON: () => ({}),
        } as DOMRect);
        (TestBed.inject(ActivatedRoute) as MockActivatedRoute).setParameters({ courseId: 123, examId: 1 });
        comp.course.set({ accuracyOfScores: 1 });
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        const navigate = vi.spyOn(TestBed.inject(ArtemisNavigationUtilService), 'routeInNewTab').mockImplementation(() => {});
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        const bars = fixture.nativeElement.querySelectorAll('jhi-exam-scores-average-scores-graph rect[role="button"]') as NodeListOf<SVGRectElement>;
        expect(bars).toHaveLength(2);
        expect(bars[1].getAttribute('aria-label')).toContain('11 Quiz');
        vi.useFakeTimers();
        try {
            bars[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
            vi.advanceTimersByTime(1000);
            expect(navigate).toHaveBeenCalledExactlyOnceWith(['course-management', 123, 'exams', 1, 'exercise-groups', 1, 'quiz-exercises', 11, 'participations']);
        } finally {
            vi.useRealTimers();
        }

        comp.toggleFilterForNonEmptySubmission();
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.aggregatedExerciseGroupResults()[0].averagePoints).toBeUndefined();
        expect(comp.aggregatedExerciseGroupResults()[1].noOfParticipantsWithFilter).toBe(1);
        expect(fixture.nativeElement.querySelectorAll('jhi-exam-scores-average-scores-graph rect[role="button"]')).toHaveLength(0);
    });

    it.each([
        { points: 20, score: 100 },
        { points: 0, score: 0 },
    ])('uses assigned attempts for participation percentages while averaging scored points ($points)', ({ points, score }) => {
        const dto = deepClone(examScoreDTO);
        dto.hasSecondCorrectionAndStarted = false;
        // Actual server contract: the server's existing participation counts describe the scored cohort.
        dto.exerciseGroups = [
            {
                id: 24,
                title: 'Automatic programming only',
                maxPoints: 20,
                numberOfParticipants: 1,
                containedExercises: [{ exerciseId: 31, title: 'Program', maxPoints: 20, numberOfParticipants: 1, exerciseType: 'ProgrammingExercise' }],
            },
        ];
        dto.studentResults = dto.studentResults.slice(0, 2);
        dto.studentResults.forEach((result, index) => {
            result.submitted = true;
            result.maxPoints = 20;
            result.overallPointsAchieved = index === 0 ? points : undefined;
            result.overallScoreAchieved = index === 0 ? score : undefined;
            result.exerciseGroupIdToExerciseResult = {
                24:
                    index === 0
                        ? {
                              exerciseId: 31,
                              title: 'Program',
                              maxScore: 20,
                              achievedPoints: points,
                              achievedScore: score,
                              hasNonEmptySubmission: true,
                          }
                        : { exerciseId: 31, title: 'Program', maxScore: 20, hasNonEmptySubmission: true },
            };
        });
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        comp.toggleFilterForNonEmptySubmission();
        fixture.detectChanges();

        const group = comp.aggregatedExerciseGroupResults()[0];
        expect(group.totalParticipants).toBe(2);
        expect(group.noOfParticipantsWithFilter).toBe(2);
        expect(group.noOfScoredParticipantsWithFilter).toBe(1);
        expect(group.averagePoints).toBe(points);
        expect(group.averagePercentage).toBe(score);
        expect(group.exerciseResults[0].totalParticipants).toBe(2);
        expect(group.exerciseResults[0].averagePoints).toBe(points);
        expect(comp.aggregatedExamResults().meanPointsTotal).toBe(points);
        expect(comp.aggregatedExamResults().noOfExamsNonEmpty).toBe(2);
        const statisticsRow = Array.from(fixture.nativeElement.querySelectorAll('tr') as NodeListOf<HTMLTableRowElement>).find(
            (row) => row.cells[1]?.textContent?.trim() === 'Automatic programming only',
        )!;
        expect(statisticsRow.cells[2].textContent?.replace(/\s+/g, ' ').trim()).toBe('2 / 2 (100 %)');
        expect(statisticsRow.cells[6].textContent?.replace(/\s+/g, ' ').trim()).toBe('2 / 2 (100 %)');
        expect(fixture.nativeElement.textContent).not.toContain('200 %');
    });

    it('retains registration and submission counts when no test attempt has a completed automatic score', () => {
        const dto = deepClone(examScoreDTO);
        for (const result of dto.studentResults) {
            result.maxPoints = 0;
            result.overallPointsAchieved = 0;
            result.overallScoreAchieved = 0;
        }
        dto.studentResults[1].maxPoints = 10;
        dto.studentResults[1].overallPointsAchieved = undefined;
        dto.studentResults[1].overallScoreAchieved = undefined;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        fixture.detectChanges();
        expect(comp.aggregatedExamResults().noOfRegisteredUsers).toBe(3);
        expect(comp.aggregatedExamResults().noOfExamsSubmitted).toBe(2);
        expect(comp.tableState.absoluteAmountOfTotalExams).toBe(3);
        expect(comp.tableState.absoluteAmountOfSubmittedExams).toBe(2);
        expect(comp.tableState.averagePointsTotal).toBe('-');
        expect(comp.tableState.averageScoreTotal).toBe('-');
        expect(comp.scores()).toEqual([]);
    });

    it('leaves a pending bonus final grade blank in the table and exported CSV', () => {
        const dto = deepClone(examScoreDTO);
        dto.studentResults[0].overallGrade = '3';
        dto.studentResults[0].gradeWithBonus = { bonusStrategy: BonusStrategy.POINTS, pending: true };
        dto.studentResults[1].gradeWithBonus = { bonusStrategy: BonusStrategy.POINTS, pending: false, finalGrade: '1.0' };
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: dto })));
        vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse({ body: toGradingScaleDTO(gradingScale) })));
        fixture.detectChanges();
        const finalGrades = Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll('[data-testid="exam-final-grade"]')).map((cell) => cell.textContent?.trim());
        expect(finalGrades.slice(0, 2)).toEqual(['-', '1.0']);
        const exported = vi.spyOn(comp, 'exportAsCsv');
        comp.exportExamResults({
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        });
        expect(exported.mock.calls[0][1][0][FINAL_GRADE_KEY]).toBe('');
        expect(exported.mock.calls[0][1][1][FINAL_GRADE_KEY]).toBe('1.0');
    });

    it('should generate csv correctly', () => {
        const noOfSubmittedExercises = examScoreDTO.studentResults.length;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();
        comp.gradingScale.set(gradingScale);
        comp.gradingScale()!.gradeSteps = [gradeStep1];
        comp.gradingScaleExists.set(true);

        const exportAsCsvStub = vi.spyOn(comp, 'exportAsCsv');
        // create csv
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
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

    it('should initialize correctly with bonus grades and plagiarism', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(
            of(new HttpResponse({ body: { ...examScoreDTO, studentResults: studentResultsWithBonusGradesAndPlagiarism } as ExamScoreDTO })),
        );
        fixture.detectChanges();
        const finalGrades = studentResultsWithBonusGradesAndPlagiarism.map((studentResult) => studentResult.gradeWithBonus.finalGrade);
        expect(comp.hasBonus()).toEqual(BonusStrategy.GRADES_DISCRETE);
        expect(comp.hasPlagiarismVerdicts()).toBe(true);
        expect(comp.hasPlagiarismVerdictsInBonusSource()).toBe(true);
        expect(comp.gradesWithBonus()).toEqual(finalGrades);
    });

    it('should initialize correctly with bonus points', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: { ...examScoreDTO, studentResults: studentResultsWithBonusPoints } as ExamScoreDTO })));
        fixture.detectChanges();
        const finalGrades = studentResultsWithBonusGradesAndPlagiarism.map((studentResult) => studentResult.gradeWithBonus.finalGrade);
        expect(comp.hasBonus()).toEqual(BonusStrategy.POINTS);
        expect(comp.hasPlagiarismVerdicts()).toBe(false);
        expect(comp.hasPlagiarismVerdictsInBonusSource()).toBe(false);
        expect(comp.gradesWithBonus()).toEqual(finalGrades);
    });

    it('should generate csv correctly with bonus grades and plagiarism', () => {
        const noOfSubmittedExercises = examScoreDTO.studentResults.length;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(
            of(new HttpResponse({ body: { ...examScoreDTO, studentResults: studentResultsWithBonusGradesAndPlagiarism } as ExamScoreDTO })),
        );
        fixture.detectChanges();
        comp.gradingScale.set(gradingScale);
        comp.gradingScale()!.gradeSteps = [gradeStep1];
        comp.gradingScaleExists.set(true);

        const exportAsCsvStub = vi.spyOn(comp, 'exportAsCsv');
        // create csv
        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
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
            studentResultsWithBonusGradesAndPlagiarism[0].gradeWithBonus.bonusGrade,
            studentResultsWithBonusGradesAndPlagiarism[0].gradeWithBonus.finalGrade,
            studentResultsWithBonusGradesAndPlagiarism[0].mostSeverePlagiarismVerdict,
            '',
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
            studentResultsWithBonusGradesAndPlagiarism[1].gradeWithBonus.bonusGrade,
            studentResultsWithBonusGradesAndPlagiarism[1].gradeWithBonus.finalGrade,
            '',
            '',
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
            studentResultsWithBonusGradesAndPlagiarism[2].gradeWithBonus.bonusGrade,
            studentResultsWithBonusGradesAndPlagiarism[2].gradeWithBonus.finalGrade,
            '',
            studentResultsWithBonusGradesAndPlagiarism[2].gradeWithBonus.mostSeverePlagiarismVerdict,
        );
    });

    it('should export as csv', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        fixture.detectChanges();

        const testOptions: CsvExportOptions = {
            fieldSeparator: CsvFieldSeparator.SEMICOLON,
            quoteCharacter: CsvQuoteStrings.QUOTES_DOUBLE,
            quoteStrings: true,
            decimalSeparator: CsvDecimalSeparator.PERIOD,
        };
        comp.exportExamResults(testOptions);
    });

    it('should set grading scale properties', () => {
        const examScoreDTOWithGrades = examScoreDTO;
        examScoreDTOWithGrades.studentResults[0].hasPassed = true;
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOWithGrades })));
        vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse({ body: toGradingScaleDTO(gradingScale) })));
        vi.spyOn(gradingService, 'sortGradeSteps').mockImplementation((steps) => steps);
        vi.spyOn(gradingService, 'findMatchingGradeStep').mockReturnValue(gradingScale.gradeSteps[0]);
        fixture.detectChanges();

        expect(comp.gradingScaleExists()).toBe(true);
        expect(comp.gradingScale()).toEqual(gradingScale);
        expect(comp.isBonus()).toBe(false);
    });

    it('should filter non-empty submissions', () => {
        comp.filterForNonEmptySubmissions.set(false);
        comp.gradingScale.set(gradingScale);
        comp.gradingScale()!.gradeSteps = [gradeStep1, gradeStep2, gradeStep3, gradeStep4];
        comp.gradingScaleExists.set(true);
        comp.exerciseGroups.set(examScoreDTO.exerciseGroups);
        comp.studentResults.set(examScoreDTO.studentResults);
        comp.examScoreDTO.set(examScoreDTO);
        comp.aggregatedExamResults.set(new AggregatedExamResult());
        comp.course.set({ accuracyOfScores: 1 });
        vi.spyOn(gradingService, 'findMatchingGradeStep').mockReturnValue(gradingScale.gradeSteps[0]);

        comp.toggleFilterForNonEmptySubmission();

        expect(comp.filterForNonEmptySubmissions()).toBe(true);
    });

    describe('test table filtering', () => {
        const examScoreDTOOnePassing = examScoreDTO;
        examScoreDTOOnePassing.studentResults[0].hasPassed = true;
        it('should set table state correctly if non empty submissions filter is activated', () => {
            vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(gradingScale) })));
            comp.filterForNonEmptySubmissions.set(false);
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
            vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(gradingScale) })));
            comp.filterForSubmittedExams.set(false);
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
            vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTOOnePassing })));
            vi.spyOn(gradingService, 'findGradingScaleForExam').mockReturnValue(of(new HttpResponse<GradingScaleDTO>({ body: toGradingScaleDTO(gradingScale) })));
            comp.ngOnInit();
            comp.filterForNonEmptySubmissions.set(false);
            comp.filterForSubmittedExams.set(false);
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

    it('should toggle median correctly', () => {
        vi.spyOn(examService, 'getExamScores').mockReturnValue(of(new HttpResponse({ body: examScoreDTO })));
        comp.isBonus.set(false);
        fixture.detectChanges();

        expect(comp.showPassedMedian()).toBe(true);

        comp.toggleMedian(MedianType.PASSED);

        expect(comp.showPassedMedian()).toBe(false);

        comp.toggleMedian(MedianType.OVERALL);

        expect(comp.overallChartMedian).toBe(50);
        expect(comp.showOverallMedian()).toBe(true);

        comp.toggleMedian(MedianType.PASSED);

        expect(comp.showPassedMedian()).toBe(true);
        expect(comp.showOverallMedian()).toBe(false);
    });

    it('should return data label correctly if noOfExamsFiltered is 0', () => {
        comp.noOfExamsFiltered = 0;

        const dataLabel = comp.formatDataLabel(0);

        expect(dataLabel).toBe('0 (0%)');
    });
});

function expectCorrectExamScoreDto(comp: ExamScoresComponent, examScoreDTO: ExamScoreDTO) {
    expect(comp.examScoreDTO()).toEqual(examScoreDTO);
    expect(comp.studentResults()).toEqual(examScoreDTO.studentResults);
    expect(comp.exerciseGroups()).toEqual(examScoreDTO.exerciseGroups);
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
    expectedBonusGrade?: number,
    expectedFinalGrade?: number | string,
    expectedPlagiarism?: string,
    expectedPlagiarismVerdictInBonus?: string,
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
    expect(userRow[BONUS_GRADE_KEY]).toBe(expectedBonusGrade);
    expect(userRow[FINAL_GRADE_KEY]).toBe(expectedFinalGrade);
    expect(userRow[PLAGIARISM_VERDICT_KEY]).toBe(expectedPlagiarism);
    expect(userRow[PLAGIARISM_VERDICT_IN_BONUS_SOURCE_KEY]).toBe(expectedPlagiarismVerdictInBonus);
}

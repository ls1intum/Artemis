import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/entities/course.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { of } from 'rxjs';
import { QuizExerciseService } from 'app/quiz/manage/quiz-exercise.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/text-exercise.service';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ModelingExerciseService } from 'app/modeling/manage/modeling-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/file-upload-exercise.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { MockProfileService } from '../../../helpers/mocks/service/mock-profile.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';

describe('Exam Exercise Row Buttons Component', () => {
    const course = new Course();
    course.id = 456;
    course.isAtLeastInstructor = true;

    const exam = new Exam();
    exam.course = course;
    exam.id = 123;

    const dueDateStat = { inTime: 0, late: 0 } as DueDateStat;

    const quizQuestions = [
        {
            id: 1,
            text: 'text1',
            invalid: false,
            exportQuiz: false,
            randomizeOrder: true,
        },
        {
            id: 2,
            text: 'text2',
            invalid: false,
            exportQuiz: false,
            randomizeOrder: true,
        },
    ];

    const fileExercise = {
        id: 1,
        type: ExerciseType.FILE_UPLOAD,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const programmingExercise = {
        id: 2,
        type: ExerciseType.PROGRAMMING,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const modelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const textExercise = {
        id: 4,
        type: ExerciseType.TEXT,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
    };
    const quizExercise = {
        title: 'MyQuiz',
        id: 5,
        type: ExerciseType.QUIZ,
        maxPoints: 100,
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        numberOfAssessmentsOfCorrectionRounds: [dueDateStat],
        quizQuestions,
    };

    let comp: ExamExerciseRowButtonsComponent;
    let fixture: ComponentFixture<ExamExerciseRowButtonsComponent>;
    let textExerciseService: TextExerciseService;
    let modelingExerciseService: ModelingExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let quizExerciseService: QuizExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamExerciseRowButtonsComponent);
        comp = fixture.componentInstance;

        textExerciseService = TestBed.inject(TextExerciseService);
        modelingExerciseService = TestBed.inject(ModelingExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        quizExerciseService = TestBed.inject(QuizExerciseService);
        programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
    });

    beforeEach(() => {
        comp.course = course;
        comp.exam = exam;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('check exam is over', () => {
        it('should check exam is over is true', () => {
            comp.latestIndividualEndDate = dayjs().subtract(1, 'days');

            const isExamOver = comp.isExamOver();

            expect(isExamOver).toBeTrue();
        });

        it('should check exam is over is false', () => {
            comp.latestIndividualEndDate = dayjs().add(1, 'days');

            const isExamOver = comp.isExamOver();

            expect(isExamOver).toBeFalse();
        });
    });

    describe('check exercise deletion', () => {
        it('should delete textExercise', () => {
            comp.exercise = textExercise;
            const textExerciseServiceDeleteStub = jest.spyOn(textExerciseService, 'delete').mockReturnValue(of(new HttpResponse<any>({ body: [] })));

            comp.deleteExercise();

            expect(textExerciseServiceDeleteStub).toHaveBeenCalledWith(textExercise.id);
        });

        it('should delete modelingExercise', () => {
            comp.exercise = modelingExercise;
            const modelingExerciseServiceDeleteStub = jest.spyOn(modelingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<any>({ body: [] })));

            comp.deleteExercise();

            expect(modelingExerciseServiceDeleteStub).toHaveBeenCalledWith(modelingExercise.id);
        });

        it('should delete fileExercise', () => {
            comp.exercise = fileExercise;
            const fileExerciseServiceDeleteStub = jest.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of(new HttpResponse<any>({ body: [] })));

            comp.deleteExercise();

            expect(fileExerciseServiceDeleteStub).toHaveBeenCalledWith(fileExercise.id);
        });

        it('should delete quizExercise', () => {
            comp.exercise = quizExercise;
            const quizExerciseServiceDeleteStub = jest.spyOn(quizExerciseService, 'delete').mockReturnValue(of(new HttpResponse<any>({ body: [] })));

            comp.deleteExercise();

            expect(quizExerciseServiceDeleteStub).toHaveBeenCalledWith(quizExercise.id);
        });

        it('should delete programmingExercise', () => {
            comp.exercise = programmingExercise;
            const programmingExerciseServiceDeleteStub = jest.spyOn(programmingExerciseService, 'delete').mockReturnValue(of(new HttpResponse<any>({ body: [] })));

            comp.deleteProgrammingExercise({ deleteStudentReposBuildPlans: true, deleteBaseReposBuildPlans: false });

            expect(programmingExerciseServiceDeleteStub).toHaveBeenCalledWith(programmingExercise.id, true, false);
        });
    });

    describe('check quiz is being exported', () => {
        it('should export quiz exercise by id', () => {
            comp.exercise = quizExercise;
            const quizExerciseServiceFindSpy = jest.spyOn(quizExerciseService, 'find').mockReturnValue(of(new HttpResponse<QuizExercise>({ body: quizExercise })));
            const quizExerciseServiceExportQuizSpy = jest.spyOn(quizExerciseService, 'exportQuiz');

            comp.exportQuizById(true);

            expect(quizExerciseServiceFindSpy).toHaveBeenCalledWith(quizExercise.id);
            expect(quizExerciseServiceExportQuizSpy).toHaveBeenCalledWith(quizQuestions, true, quizExercise.title);
        });
    });
});

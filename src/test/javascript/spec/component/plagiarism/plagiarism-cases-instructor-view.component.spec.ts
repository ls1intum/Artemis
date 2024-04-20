import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { PlagiarismCasesInstructorViewComponent } from 'app/course/plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, ActivatedRouteSnapshot, convertToParamMap } from '@angular/router';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { TranslateService } from '@ngx-translate/core';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';
import * as DownloadUtil from 'app/shared/util/download.util';
import dayjs from 'dayjs/esm';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { MockComponent } from 'ng-mocks';
import { NotificationService } from 'app/shared/notification/notification.service';
import { MockNotificationService } from '../../helpers/mocks/service/mock-notification.service';

jest.mock('app/shared/util/download.util', () => ({
    downloadFile: jest.fn(),
}));

describe('Plagiarism Cases Instructor View Component', () => {
    let component: PlagiarismCasesInstructorViewComponent;
    let fixture: ComponentFixture<PlagiarismCasesInstructorViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;

    let route: ActivatedRoute;

    const date = dayjs();

    const exercise1 = {
        id: 1,
        title: 'Test Exercise 1',
    } as TextExercise;
    const exercise2 = {
        id: 2,
        title: 'Test Exercise 2',
    } as TextExercise;

    const plagiarismCase1 = {
        id: 1,
        exercise: exercise1,
        student: { id: 1, login: 'Student 1' },
        verdict: PlagiarismVerdict.PLAGIARISM,
        verdictBy: {
            name: 'Test Instructor 1',
        },
        verdictDate: date,
        post: {
            id: 1,
            answers: [
                {
                    author: { id: 1 },
                },
            ],
        },
    } as PlagiarismCase;
    const plagiarismCase2 = {
        id: 2,
        student: { id: 2 },
        exercise: exercise1,
        verdict: PlagiarismVerdict.WARNING,
    } as PlagiarismCase;
    const plagiarismCase3 = {
        id: 3,
        student: { id: 3 },
        exercise: exercise2,
        verdict: PlagiarismVerdict.POINT_DEDUCTION,
        post: { id: 2 },
    } as PlagiarismCase;
    const plagiarismCase4 = {
        id: 4,
        student: { id: 4, login: 'Student 2' },
        exercise: exercise2,
    } as PlagiarismCase;
    const plagiarismCase5 = {
        id: 5,
        student: { id: 5, login: 'Student 2' },
        exercise: exercise2,
        verdict: PlagiarismVerdict.NO_PLAGIARISM,
        post: { id: 3 },
    } as PlagiarismCase;

    beforeEach(() => {
        route = { snapshot: { paramMap: convertToParamMap({ courseId: 1 }) } } as any as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCasesInstructorViewComponent, MockComponent(DocumentationButtonComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: NotificationService, useClass: MockNotificationService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCasesInstructorViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = fixture.debugElement.injector.get(PlagiarismCasesService);
        jest.spyOn(plagiarismCasesService, 'getCoursePlagiarismCasesForInstructor').mockReturnValue(
            of({ body: [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4] }) as Observable<HttpResponse<PlagiarismCase[]>>,
        );
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set plagiarism cases and exercises on initialization', fakeAsync(() => {
        component.ngOnInit();
        tick();
        expect(component.courseId).toBe(1);
        expect(component.examId).toBe(0);
        expect(component.plagiarismCases).toEqual([plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4]);
        expect(component.exercisesWithPlagiarismCases).toEqual([exercise1, exercise2]);
        expect(component.groupedPlagiarismCases).toEqual({ 1: [plagiarismCase1, plagiarismCase2], 2: [plagiarismCase3, plagiarismCase4] });
    }));

    it('should get plagiarism cases for course when exam id is not set', fakeAsync(() => {
        jest.spyOn(plagiarismCasesService, 'getExamPlagiarismCasesForInstructor');
        component.ngOnInit();
        tick();

        expect(component.courseId).toBe(1);
        expect(component.examId).toBe(0);
        expect(plagiarismCasesService.getCoursePlagiarismCasesForInstructor).toHaveBeenCalledOnce();
        expect(plagiarismCasesService.getExamPlagiarismCasesForInstructor).not.toHaveBeenCalled();
    }));

    it('should get plagiarism cases for exam when exam id is set', fakeAsync(() => {
        jest.spyOn(plagiarismCasesService, 'getExamPlagiarismCasesForInstructor');

        const newSnapshot = { paramMap: convertToParamMap({ courseId: 1, examId: 1 }) } as ActivatedRouteSnapshot;
        const activatedRoute: ActivatedRoute = fixture.debugElement.injector.get(ActivatedRoute);
        activatedRoute.snapshot = newSnapshot;

        component.ngOnInit();
        tick();

        expect(component.courseId).toBe(1);
        expect(component.examId).toBe(1);
        expect(plagiarismCasesService.getCoursePlagiarismCasesForInstructor).not.toHaveBeenCalled();
        expect(plagiarismCasesService.getExamPlagiarismCasesForInstructor).toHaveBeenCalledOnce();
    }));

    it('should calculate number of plagiarism cases', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4, plagiarismCase5];
        expect(component.numberOfCases(plagiarismCases)).toBe(5);
    });

    it('should calculate number of plagiarism cases with verdict', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4, plagiarismCase5];
        expect(component.numberOfCasesWithVerdict(plagiarismCases)).toBe(4);
    });

    it('should calculate percentage of plagiarism cases with verdict', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4, plagiarismCase5];
        expect(component.percentageOfCasesWithVerdict(plagiarismCases)).toBe(80);
    });

    it('should calculate number of plagiarism cases with post', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4, plagiarismCase5];
        expect(component.numberOfCasesWithPost(plagiarismCases)).toBe(3);
    });

    it('should calculate percentage of plagiarism cases with post', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.percentageOfCasesWithPost(plagiarismCases)).toBe(50);
    });

    it('should calculate number of plagiarism cases with student answer', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4, plagiarismCase5];
        expect(component.numberOfCasesWithStudentAnswer(plagiarismCases)).toBe(1);
    });

    it('should calculate percentage of plagiarism cases with student answer', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.percentageOfCasesWithStudentAnswer(plagiarismCases)).toBe(50);
    });

    it('should check if student has responded for a plagiarism case', () => {
        expect(component.hasStudentAnswer(plagiarismCase1)).toBeTrue();
        expect(component.hasStudentAnswer(plagiarismCase2)).toBeFalse();
        expect(component.hasStudentAnswer(plagiarismCase3)).toBeFalse();
    });

    it('should export plagiarism cases as CSV', () => {
        const downloadSpy = jest.spyOn(DownloadUtil, 'downloadFile');
        component.plagiarismCases = [plagiarismCase1, plagiarismCase4];
        const expectedBlob = [
            'Student Login,Exercise,Verdict, Verdict Date\n',
            `Student 1, Test Exercise 1, PLAGIARISM, ${date}, Test Instructor 1\n`,
            'Student 2, Test Exercise 2, No verdict yet, -, -\n',
        ];
        component.exportPlagiarismCases();
        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy).toHaveBeenCalledWith(new Blob(expectedBlob, { type: 'text/csv' }), 'plagiarism-cases.csv');
    });
});

import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { PlagiarismCasesInstructorViewComponent } from 'app/course/plagiarism-cases/instructor-view/plagiarism-cases-instructor-view.component';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { TranslateService } from '@ngx-translate/core';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismVerdict } from 'app/exercises/shared/plagiarism/types/PlagiarismVerdict';

describe('Plagiarism Cases Student View Component', () => {
    let component: PlagiarismCasesInstructorViewComponent;
    let fixture: ComponentFixture<PlagiarismCasesInstructorViewComponent>;
    let plagiarismCasesService: PlagiarismCasesService;

    const route = { snapshot: { paramMap: convertToParamMap({ courseId: 1 }) } } as any as ActivatedRoute;

    const exercise1 = {
        id: 1,
    } as TextExercise;
    const exercise2 = {
        id: 2,
    } as TextExercise;

    const plagiarismCase1 = {
        id: 1,
        exercise: exercise1,
        verdict: PlagiarismVerdict.PLAGIARISM,
        post: { id: 1 },
    } as PlagiarismCase;
    const plagiarismCase2 = {
        id: 2,
        exercise: exercise1,
        verdict: PlagiarismVerdict.WARNING,
    } as PlagiarismCase;
    const plagiarismCase3 = {
        id: 3,
        exercise: exercise2,
        verdict: PlagiarismVerdict.POINT_DEDUCTION,
        post: { id: 2 },
    } as PlagiarismCase;
    const plagiarismCase4 = {
        id: 4,
        exercise: exercise2,
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateTestingModule],
            declarations: [PlagiarismCasesInstructorViewComponent],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PlagiarismCasesInstructorViewComponent);
        component = fixture.componentInstance;
        plagiarismCasesService = fixture.debugElement.injector.get(PlagiarismCasesService);
        jest.spyOn(plagiarismCasesService, 'getPlagiarismCasesForInstructor').mockReturnValue(
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
        expect(component.plagiarismCases).toEqual([plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4]);
        expect(component.exercisesWithPlagiarismCases).toEqual([exercise1, exercise2]);
        expect(component.groupedPlagiarismCases).toEqual({ 1: [plagiarismCase1, plagiarismCase2], 2: [plagiarismCase3, plagiarismCase4] });
    }));

    it('should calculate number of plagiarism cases', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.numberOfCases(plagiarismCases)).toBe(4);
    });

    it('should calculate number of plagiarism cases with verdict', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.numberOfCasesWithVerdict(plagiarismCases)).toBe(3);
    });

    it('should calculate percentage of plagiarism cases with verdict', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.percentageOfCasesWithVerdict(plagiarismCases)).toBe(75);
    });

    it('should calculate number of plagiarism cases with post', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.numberOfCasesWithPost(plagiarismCases)).toBe(2);
    });

    it('should calculate percentage of plagiarism cases with post', () => {
        const plagiarismCases = [plagiarismCase1, plagiarismCase2, plagiarismCase3, plagiarismCase4];
        expect(component.percentageOfCasesWithPost(plagiarismCases)).toBe(50);
    });
});

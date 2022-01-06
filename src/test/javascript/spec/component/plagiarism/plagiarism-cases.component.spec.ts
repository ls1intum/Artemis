import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { PlagiarismCasesComponent } from 'app/course/plagiarism-cases/plagiarism-cases.component';
import { ArtemisTestModule } from '../../test.module';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { MockComponent, MockDirective } from 'ng-mocks';
import { ProgressBarComponent } from 'app/shared/dashboards/tutor-participation-graph/progress-bar/progress-bar.component';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { EntityArrayResponseType, PlagiarismCasesService } from 'app/course/plagiarism-cases/plagiarism-cases.service';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';

describe('Plagiarism Cases Component', () => {
    let comp: PlagiarismCasesComponent;
    let fixture: ComponentFixture<PlagiarismCasesComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let getPlagiarismCasesStub: jest.SpyInstance;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const instructorStatement1A = 'instructorStatement for student 1A';
    const instructorStatement1B = 'instructorStatement for student 1B';
    const instructorStatement2A = 'instructorStatement for student 2A';
    const instructorStatement3A = 'instructorStatement for student 3A';
    const instructorStatement4A = 'instructorStatement for student 4A';
    const instructorStatement4B = 'instructorStatement for student 4B';

    const studentStatement1A = 'statement 1A';
    const studentStatement1B = 'statement 1B';
    const studentStatement2A = 'statement 2A';
    const studentStatement3A = 'statement 3A';
    const studentStatement3B = 'statement 3B';

    const plagiarismComparison1 = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        instructorStatementA: instructorStatement1A,
        instructorStatementB: instructorStatement1B,
        statusA: PlagiarismStatus.CONFIRMED,
        statusB: PlagiarismStatus.CONFIRMED,
        studentStatementA: studentStatement1A,
        studentStatementB: studentStatement1B,
        similarity: 0.5,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    const plagiarismComparison2 = {
        id: 2,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        instructorStatementA: instructorStatement2A,
        statusA: PlagiarismStatus.DENIED,
        studentStatementA: studentStatement2A,
        similarity: 0.7,
        status: PlagiarismStatus.DENIED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    const plagiarismComparison3 = {
        id: 3,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        instructorStatementA: instructorStatement3A,
        statusA: PlagiarismStatus.CONFIRMED,
        statusB: PlagiarismStatus.CONFIRMED,
        studentStatementA: studentStatement3A,
        studentStatementB: studentStatement3B,
        similarity: 0.4,
        status: PlagiarismStatus.CONFIRMED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;
    const plagiarismComparison4 = {
        id: 4,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        instructorStatementA: instructorStatement4A,
        instructorStatementB: instructorStatement4B,
        similarity: 0.6,
        status: PlagiarismStatus.DENIED,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    const textExercise1 = { id: 1, type: ExerciseType.TEXT } as TextExercise;
    const textExercise2 = { id: 2, type: ExerciseType.TEXT } as TextExercise;

    const plagiarismCase1 = {
        exercise: textExercise1,
        comparisons: [plagiarismComparison1, plagiarismComparison2],
    } as PlagiarismCase;
    const plagiarismCase2 = {
        exercise: textExercise2,
        comparisons: [plagiarismComparison3, plagiarismComparison4],
    } as PlagiarismCase;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PlagiarismCasesComponent, TranslatePipeMock, MockDirective(NgModel), MockComponent(ProgressBarComponent), MockComponent(PlagiarismCasesListComponent)],
            providers: [{ provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ courseId: 1 }) } } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismCasesComponent);
                comp = fixture.componentInstance;

                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                getPlagiarismCasesStub = jest
                    .spyOn(plagiarismCasesService, 'getPlagiarismCases')
                    .mockReturnValue(of({ body: [plagiarismCase1, plagiarismCase2] } as EntityArrayResponseType));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize the plagiarism cases', fakeAsync(() => {
        comp.ngOnInit();

        tick();

        expect(comp.courseId).toBe(1);
        expect(comp.confirmedPlagiarismCases).toEqual([plagiarismCase1, plagiarismCase2]);
        expect(getPlagiarismCasesStub).toHaveBeenCalledWith(1);
    }));

    it('should calculate the number of instructor statements', () => {
        comp.confirmedPlagiarismCases = [plagiarismCase1, plagiarismCase2];

        expect(comp.numberOfInstructorStatements()).toBe(6);

        comp.confirmedPlagiarismCases = [plagiarismCase1];

        expect(comp.numberOfInstructorStatements()).toBe(3);
    });

    it('should calculate the number of plagiarism cases', () => {
        comp.confirmedPlagiarismCases = [plagiarismCase1, plagiarismCase2];

        expect(comp.numberOfCases()).toBe(8);

        comp.confirmedPlagiarismCases = [plagiarismCase1];

        expect(comp.numberOfCases()).toBe(4);
    });

    it('should calculate the number of student statements', () => {
        comp.confirmedPlagiarismCases = [plagiarismCase1, plagiarismCase2];

        expect(comp.numberOfStudentStatements()).toBe(5);

        comp.confirmedPlagiarismCases = [plagiarismCase1];

        expect(comp.numberOfStudentStatements()).toBe(3);
    });

    it('should calculate the number of final statuses', () => {
        comp.confirmedPlagiarismCases = [plagiarismCase1, plagiarismCase2];

        expect(comp.numberOfFinalStatuses()).toBe(5);

        comp.confirmedPlagiarismCases = [plagiarismCase1];

        expect(comp.numberOfFinalStatuses()).toBe(3);
    });
});

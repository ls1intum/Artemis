import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { NgModel } from '@angular/forms';
import { MockComponent, MockDirective } from 'ng-mocks';
import { PlagiarismCasesListComponent } from 'app/course/plagiarism-cases/plagiarism-cases-list.component';
import { ArtemisTestModule } from '../../test.module';
import { PlagiarismStatus } from 'app/exercises/shared/plagiarism/types/PlagiarismStatus';
import { PlagiarismComparison } from 'app/exercises/shared/plagiarism/types/PlagiarismComparison';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { PlagiarismCasesService } from 'app/course/plagiarism-cases/shared/plagiarism-cases.service';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { PlagiarismSplitViewComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/plagiarism-split-view.component';
import { ModelingSubmissionElement } from 'app/exercises/shared/plagiarism/types/modeling/ModelingSubmissionElement';
import { HttpResponse } from '@angular/common/http';

describe('Plagiarism Cases List Component', () => {
    let comp: PlagiarismCasesListComponent;
    let fixture: ComponentFixture<PlagiarismCasesListComponent>;
    let plagiarismCasesService: PlagiarismCasesService;
    let saveInstructorStatementStub: jest.SpyInstance;
    let updatePlagiarismComparisonFinalStatusStub: jest.SpyInstance;

    const studentLoginA = 'student1A';
    const studentLoginB = 'student1B';

    const instructorStatementA = 'instructor statement text a';
    const instructorStatementB = 'instructor statement text b';

    const plagiarismComparison = {
        id: 1,
        submissionA: { studentLogin: studentLoginA },
        submissionB: { studentLogin: studentLoginB },
        similarity: 0.5,
        status: PlagiarismStatus.NONE,
    } as PlagiarismComparison<TextSubmissionElement | ModelingSubmissionElement>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [PlagiarismCasesListComponent, TranslatePipeMock, MockDirective(NgModel), MockComponent(PlagiarismSplitViewComponent)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PlagiarismCasesListComponent);
                comp = fixture.componentInstance;

                plagiarismCasesService = TestBed.inject(PlagiarismCasesService);
                saveInstructorStatementStub = jest.spyOn(plagiarismCasesService, 'saveInstructorStatement');
                updatePlagiarismComparisonFinalStatusStub = jest
                    .spyOn(plagiarismCasesService, 'updatePlagiarismComparisonFinalStatus')
                    .mockReturnValue(of({} as HttpResponse<void>));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should have instructor statement for student A', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.plagiarismComparison.instructorStatementA = instructorStatementA;

        expect(comp.hasInstructorStatementA()).toBe(true);
    });

    it('should not have instructor statement for student A', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.plagiarismComparison.instructorStatementA = undefined;
        comp.plagiarismComparison.instructorStatementB = undefined;

        expect(comp.hasInstructorStatementA()).toBe(false);
    });

    it('should have instructor statement for student B', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.plagiarismComparison.instructorStatementB = instructorStatementB;

        expect(comp.hasInstructorStatementB()).toBe(true);
    });

    it('should not have instructor statement for student B', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.plagiarismComparison.instructorStatementA = undefined;
        comp.plagiarismComparison.instructorStatementB = undefined;

        expect(comp.hasInstructorStatementB()).toBe(false);
    });

    it('should hide instructor statement form', () => {
        comp.activeStudentLogin = studentLoginA;
        comp.activeComparisonId = plagiarismComparison.id;

        comp.hideInstructorStatementForm();

        expect(comp.activeStudentLogin).toBe(undefined);
        expect(comp.activeComparisonId).toBe(undefined);
    });

    it('should show instructor statement form', () => {
        comp.showInstructorStatementForm(studentLoginA, plagiarismComparison.id);

        expect(comp.activeStudentLogin).toBe(studentLoginA);
        expect(comp.activeComparisonId).toBe(plagiarismComparison.id);
    });

    it('should show comparison', () => {
        comp.showComparison(1);

        expect(comp.activeSplitViewComparison).toBe(1);
    });

    it('should save instructor statement for student a', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.instructorStatement = 'instructor statement text a';
        saveInstructorStatementStub.mockReturnValue(of({ body: instructorStatementA }));
        comp.courseId = 1;

        comp.saveInstructorStatement('A');

        expect(comp.plagiarismComparison.instructorStatementA).toBe(instructorStatementA);
        expect(saveInstructorStatementStub).toHaveBeenCalledWith(1, plagiarismComparison.id, studentLoginA, 'instructor statement text a');
    });

    it('should save instructor statement for student b', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.instructorStatement = 'instructor statement text b';
        saveInstructorStatementStub.mockReturnValue(of({ body: instructorStatementB }));
        comp.courseId = 1;

        comp.saveInstructorStatement('B');

        expect(comp.plagiarismComparison.instructorStatementB).toBe(instructorStatementB);
        expect(saveInstructorStatementStub).toHaveBeenCalledWith(1, plagiarismComparison.id, studentLoginB, 'instructor statement text b');
    });

    it('should update status to confirmed for student a', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.courseId = 1;

        comp.updateStatus(true, studentLoginA);

        expect(comp.plagiarismComparison.statusA).toBe(PlagiarismStatus.CONFIRMED);
        expect(updatePlagiarismComparisonFinalStatusStub).toHaveBeenCalledWith(1, plagiarismComparison.id, true, studentLoginA);
    });

    it('should update status to denied for student a', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.courseId = 1;

        comp.updateStatus(false, studentLoginA);

        expect(comp.plagiarismComparison.statusA).toBe(PlagiarismStatus.DENIED);
        expect(updatePlagiarismComparisonFinalStatusStub).toHaveBeenCalledWith(1, plagiarismComparison.id, false, studentLoginA);
    });

    it('should update status to confirmed for student b', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.courseId = 1;

        comp.updateStatus(true, studentLoginB);

        expect(comp.plagiarismComparison.statusB).toBe(PlagiarismStatus.CONFIRMED);
        expect(updatePlagiarismComparisonFinalStatusStub).toHaveBeenCalledWith(1, plagiarismComparison.id, true, studentLoginB);
    });

    it('should update status to denied for student b', () => {
        comp.plagiarismComparison = plagiarismComparison;
        comp.courseId = 1;

        comp.updateStatus(false, studentLoginB);

        expect(comp.plagiarismComparison.statusB).toBe(PlagiarismStatus.DENIED);
        expect(updatePlagiarismComparisonFinalStatusStub).toHaveBeenCalledWith(1, plagiarismComparison.id, false, studentLoginB);
    });
});

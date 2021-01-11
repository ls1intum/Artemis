import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { Course } from 'app/entities/course.model';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';
import { StudentsExamImportDialogComponent } from 'app/exam/manage/students/students-exam-import-dialog/students-exam-import-dialog.component';
import { TranslatePipe } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { JhiTranslateDirective } from 'ng-jhipster';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { DebugElement } from '@angular/core';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { JhiAlertService } from 'ng-jhipster';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { By } from '@angular/platform-browser';
import { StudentDTO } from 'app/entities/student-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentExamImportDialogComponent', () => {
    let fixture: ComponentFixture<StudentsExamImportDialogComponent>;
    let component: StudentsExamImportDialogComponent;
    let examManagementService: ExamManagementService;

    const studentCsvColumns = 'REGISTRATION_NUMBER,FIRST_NAME_OF_STUDENT,FAMILY_NAME_OF_STUDENT';

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [
                StudentsExamImportDialogComponent,
                MockDirective(JhiTranslateDirective),
                MockPipe(TranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(HelpIconComponent),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
            ],
            providers: [{ provide: NgbActiveModal, useValue: sinon.createStubInstance(NgbActiveModal) }, MockProvider(JhiAlertService), MockProvider(ExamManagementService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsExamImportDialogComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);

                component.courseId = course.id!;
                component.exam = exam;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
    });

    it('should reset dialog when selecting csv file', async () => {
        component.studentsToImport = [{ registrationNumber: '1', lastName: 'lastName', firstName: 'firstName' }];
        component.notFoundStudents = [{ registrationNumber: '2', lastName: 'lastName', firstName: 'firstName' }];
        component.hasImported = true;

        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.studentsToImport).to.be.empty;
        expect(component.notFoundStudents).to.be.empty;
    });

    it('should read no students from csv file', async () => {
        const event = { target: { files: [studentCsvColumns] } };
        await component.onCSVFileSelect(event);

        expect(component.studentsToImport).to.be.empty;
        expect(component.notFoundStudents).to.be.empty;
    });

    it('should read students from csv file', async () => {
        const csv = `${studentCsvColumns}\n"1","Max","Mustermann"\n"2","John","Wick"`;
        const event = { target: { files: [csv] } };
        await component.onCSVFileSelect(event);

        expect(component.studentsToImport.length).to.equal(2);
        expect(component.notFoundStudents).to.be.empty;
    });

    it('should have validation error for invalid csv', async () => {
        // Csv without header
        const invalidCsv = `"1","Max","Mustermann"\n"2","John","Wick"`;

        const event = { target: { files: [invalidCsv] } };
        await component.onCSVFileSelect(event);

        expect(component.validationError).to.not.be.empty;
    });

    it('should import students', function () {
        const studentsToImport: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross' },
        ];
        const studentsNotFound: StudentDTO[] = [{ registrationNumber: '2', firstName: 'Bob', lastName: 'Ross' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.studentsToImport = studentsToImport;
        component.importStudents();

        expect(examManagementService.addStudentsToExam).to.have.been.calledOnce;
        expect(component.isImporting).to.be.false;
        expect(component.hasImported).to.be.true;
        expect(component.notFoundStudents.length).to.equal(studentsNotFound.length);
    });

    it('should compute invalid student entries', function () {
        let rowNumbersOrNull = component.computeInvalidStudentEntries([{ FIRST_NAME_OF_STUDENT: 'Max' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ FIRST_NAME_OF_STUDENT: 'Max' }, { REGISTRATION_NUMBER: '1' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ FIRST_NAME_OF_STUDENT: 'Max' }, { LAST_NAME_OF_STUDENT: 'Mustermann' }]);
        expect(rowNumbersOrNull).to.equal('2, 3');

        rowNumbersOrNull = component.computeInvalidStudentEntries([]);
        expect(rowNumbersOrNull).to.be.null;
    });

    it('should import correctly', function () {
        const importedStudents: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross' },
        ];
        const notImportedStudents: StudentDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude' }];

        const fakeResponse = { body: notImportedStudents } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.studentsToImport = importedStudents.concat(notImportedStudents);
        component.importStudents();

        importedStudents.forEach((student) => expect(component.wasImported(student)).to.be.true);
        notImportedStudents.forEach((student) => expect(component.wasImported(student)).to.be.false);

        expect(component.numberOfStudentsImported).to.equal(importedStudents.length);
        expect(component.numberOfStudentsNotImported).to.equal(notImportedStudents.length);
    });
});

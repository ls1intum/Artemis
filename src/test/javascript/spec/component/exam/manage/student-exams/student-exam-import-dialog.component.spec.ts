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
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { JhiAlertService } from 'ng-jhipster';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { StudentDTO } from 'app/entities/student-dto.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { By } from '@angular/platform-browser';

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
        component.studentsToImport = [{ registrationNumber: '1', lastName: 'lastName', firstName: 'firstName', login: 'login1' }];
        component.notFoundStudents = [{ registrationNumber: '2', lastName: 'lastName2', firstName: 'firstName2', login: 'login2' }];
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
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const studentsNotFound: StudentDTO[] = [{ registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' }];

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
        let rowNumbersOrNull = component.computeInvalidStudentEntries([{ firstnameofstudent: 'Max' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ firstnameofstudent: 'Max' }, { registrationnumber: '1' }, { login: 'username' }]);
        expect(rowNumbersOrNull).to.equal('2');

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ benutzer: 'Max' }, { benutzername: '1' }, { user: 'username' }]);
        expect(rowNumbersOrNull).to.be.null;

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ matriculationnumber: '1' }, { matrikelnummer: '1' }]);
        expect(rowNumbersOrNull).to.be.null;

        rowNumbersOrNull = component.computeInvalidStudentEntries([{ firstnameofstudent: 'Max' }, { familynameofstudent: 'Mustermann' }]);
        expect(rowNumbersOrNull).to.equal('2, 3');

        rowNumbersOrNull = component.computeInvalidStudentEntries([]);
        expect(rowNumbersOrNull).to.be.null;
    });

    it('should import correctly', function () {
        const importedStudents: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Musetermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const notImportedStudents: StudentDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3' }];

        const fakeResponse = { body: notImportedStudents } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.studentsToImport = importedStudents.concat(notImportedStudents);
        component.importStudents();

        importedStudents.forEach((student) => expect(component.wasImported(student)).to.be.true);
        notImportedStudents.forEach((student) => expect(component.wasImported(student)).to.be.false);

        expect(component.numberOfStudentsImported).to.equal(importedStudents.length);
        expect(component.numberOfStudentsNotImported).to.equal(notImportedStudents.length);
    });

    it('should invoke REST call on "Import" but not on "Finish"', function () {
        const studentsToImport: StudentDTO[] = [
            { registrationNumber: '1', firstName: 'Max', lastName: 'Mustermann', login: 'login1' },
            { registrationNumber: '2', firstName: 'Bob', lastName: 'Ross', login: 'login2' },
        ];
        const studentsNotFound: StudentDTO[] = [{ registrationNumber: '3', firstName: 'Some', lastName: 'Dude', login: 'login3' }];

        const fakeResponse = { body: studentsNotFound } as HttpResponse<StudentDTO[]>;
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.studentsToImport = studentsToImport;

        fixture.detectChanges();

        expect(component.hasImported).to.be.false;
        expect(component.isSubmitDisabled).to.be.false;
        const importButton = fixture.debugElement.query(By.css('#import'));
        expect(importButton).to.exist;

        importButton.nativeElement.click();

        expect(examManagementService.addStudentsToExam).to.have.been.calledOnce;
        expect(component.isImporting).to.be.false;
        expect(component.hasImported).to.be.true;
        expect(component.notFoundStudents.length).to.equal(studentsNotFound.length);

        // Reset call counter
        sinon.restore();
        sinon.replace(examManagementService, 'addStudentsToExam', sinon.fake.returns(of(fakeResponse)));

        component.hasImported = true;
        fixture.detectChanges();

        const finishButton = fixture.debugElement.query(By.css('#finish-button'));
        expect(finishButton).to.exist;

        finishButton.nativeElement.click();

        expect(examManagementService.addStudentsToExam).to.not.have.been.called;
    });
});

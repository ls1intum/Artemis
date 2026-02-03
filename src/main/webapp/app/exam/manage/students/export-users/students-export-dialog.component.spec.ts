import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StudentsExportDialogComponent } from 'app/exam/manage/students/export-users/students-export-dialog.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { MockDirective, MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExportExamUserDTO } from 'app/exam/manage/students/export-users/students-export.model';
import { DialogModule } from 'primeng/dialog';
import { ButtonModule } from 'primeng/button';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { fakeAsync, tick } from '@angular/core/testing';

describe('StudentsExportDialogComponent', () => {
    let component: StudentsExportDialogComponent;
    let fixture: ComponentFixture<StudentsExportDialogComponent>;
    let examManagementService: ExamManagementService;

    const course: Course = { id: 1 };
    const exam: Exam = { id: 2, title: 'Final Exam', course };

    const exportUsers: ExportExamUserDTO[] = [
        {
            matriculationNumber: '123456',
            login: 's1',
            name: 'Student One',
            email: 'one@example.com',
            room: 'Room A',
            seat: '1',
            fullLocation: 'Building A',
        },
        {
            matriculationNumber: '=HACK',
            login: '+evil',
            name: '@name',
            email: '-mail',
            room: undefined,
            seat: undefined,
            fullLocation: undefined,
        },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent, DialogModule, ButtonModule],
            declarations: [MockDirective(TranslateDirective)],
            providers: [MockProvider(ExamManagementService), { provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(StudentsExportDialogComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);

        examManagementService = TestBed.inject(ExamManagementService);

        jest.spyOn(component as any, 'downloadBlob').mockImplementation(() => {});
        component.openDialog();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open the dialog', () => {
        expect(component.dialogVisible()).toBeTrue();
    });

    it('should close the dialog when cancel button is clicked', () => {
        fixture.detectChanges();

        const button = document.body.querySelector('#cancel-button') as HTMLButtonElement;
        button.click();

        expect(component.dialogVisible()).toBeFalse();
        expect(component.lastExportAttemptFailed()).toBeFalse();
    });

    it('should call exportExamUsers with correct arguments', () => {
        const exportSpy = jest.spyOn(examManagementService, 'exportExamUsers').mockReturnValue(of(exportUsers));

        component.exportUsers();

        expect(exportSpy).toHaveBeenCalledOnce();
        expect(exportSpy).toHaveBeenCalledWith(course.id, exam.id);
    });

    it('should download CSV and close dialog on successful export', fakeAsync(() => {
        jest.spyOn(examManagementService, 'exportExamUsers').mockReturnValue(of(exportUsers));

        const downloadSpy = jest.spyOn(component as any, 'downloadBlob').mockImplementation(() => {});
        const closeSpy = jest.spyOn(component, 'closeDialog');

        component.exportUsers();

        tick();
        fixture.detectChanges();

        expect(downloadSpy).toHaveBeenCalledOnce();
        expect(downloadSpy.mock.calls[0][1]).toBe(`exam-${exam.id}-students.csv`);
        expect(closeSpy).toHaveBeenCalled();
    }));

    it('should set error flag when export fails', () => {
        jest.spyOn(examManagementService, 'exportExamUsers').mockReturnValue(throwError(() => new Error('boom')));

        component.exportUsers();

        expect(component.lastExportAttemptFailed()).toBeTrue();
        expect(component.dialogVisible()).toBeTrue();
    });

    it('should reset error flag when dialog is closed', () => {
        component.lastExportAttemptFailed.set(true);

        component.closeDialog();

        expect(component.lastExportAttemptFailed()).toBeFalse();
        expect(component.dialogVisible()).toBeFalse();
    });

    it('should sanitize dangerous CSV values', () => {
        const sanitize = (component as any).sanitizeCsvValue.bind(component);

        expect(sanitize('=SUM(A1:A2)')).toBe('\t=SUM(A1:A2)');
        expect(sanitize('+1')).toBe('\t+1');
        expect(sanitize('-2')).toBe('\t-2');
        expect(sanitize('@cmd')).toBe('\t@cmd');

        expect(sanitize('safe')).toBe('safe');
        expect(sanitize(undefined)).toBeUndefined();
    });

    it('should generate a valid CSV blob', async () => {
        const blob = (component as any).createCsv(exportUsers) as Blob;

        expect(blob).toBeInstanceOf(Blob);
        expect(blob.type).toContain('text/csv');

        const text = await readBlobAsText(blob);

        expect(text).toContain('artemisApp.exam.examUsers.export.header.matriculationNumber');
        expect(text).toContain('artemisApp.exam.examUsers.export.header.login');
    });

    function readBlobAsText(blob: Blob): Promise<string> {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.onload = () => resolve(reader.result as string);
            reader.onerror = reject;
            reader.readAsText(blob);
        });
    }

    it('should not throw when exporting empty user list', () => {
        jest.spyOn(examManagementService, 'exportExamUsers').mockReturnValue(of([]));

        expect(() => component.exportUsers()).not.toThrow();
    });
});

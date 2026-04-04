import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { HttpResponse } from '@angular/common/http';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { DialogModule } from 'primeng/dialog';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import * as readUsersFromCsv from 'app/shared/user-import/util/read-users-from-csv';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisterStudentRequest } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupRegisteredStudentsService } from 'app/tutorialgroup/manage/service/tutorial-group-registered-students.service';
import { TutorialRegistrationsImportModalTableComponent } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal-table/tutorial-registrations-import-modal-table.component';
import { ImportFlowStep, TutorialRegistrationsImportModalComponent } from './tutorial-registrations-import-modal.component';
import { LoadingIndicatorOverlayComponent } from 'app/shared/loading-indicator-overlay/loading-indicator-overlay.component';
import { MockTranslateService } from 'src/test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { LoadingIndicatorOverlayStubComponent } from 'src/test/javascript/spec/helpers/stubs/tutorialgroup/loading-indicator-overlay-stub.component';
import { PrimeNgDialogStubComponent } from 'src/test/javascript/spec/helpers/stubs/tutorialgroup/prime-ng-dialog-stub.component';
import { TutorialRegistrationsImportModalTableStubComponent } from 'src/test/javascript/spec/helpers/stubs/tutorialgroup/tutorial-registrations-import-modal-table-stub.component';
import { TutorialRegistrationsImportModalTableRow } from 'app/tutorialgroup/manage/tutorial-registrations-import-modal-table/tutorial-registrations-import-modal-table.component';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

interface TutorialGroupApiServiceMock {
    importRegistrations: ReturnType<typeof vi.fn>;
}

interface TutorialGroupRegisteredStudentsServiceMock {
    fetchRegisteredStudents: ReturnType<typeof vi.fn>;
}

enum ResultsStepCase {
    ALL_REGISTERED = 'ALL_REGISTERED',
    SOME_REGISTERED = 'SOME_REGISTERED',
    NONE_REGISTERED = 'NONE_REGISTERED',
}

describe('TutorialRegistrationsImportModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TutorialRegistrationsImportModalComponent;
    let fixture: ComponentFixture<TutorialRegistrationsImportModalComponent>;

    let alertServiceMock: AlertServiceMock;
    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let tutorialGroupRegisteredStudentsServiceMock: TutorialGroupRegisteredStudentsServiceMock;

    const firstParsedStudent: StudentDTO = { login: 'ada', registrationNumber: 'R001', firstName: '', lastName: '', email: '' };
    const secondParsedStudent: StudentDTO = { login: 'alan', registrationNumber: 'R002', firstName: '', lastName: '', email: '' };
    const thirdParsedStudent: StudentDTO = { login: '', registrationNumber: 'R003', firstName: '', lastName: '', email: '' };
    const firstStudent: TutorialGroupRegisterStudentRequest = { login: 'ada', registrationNumber: 'R001' };
    const secondStudent: TutorialGroupRegisterStudentRequest = { login: 'alan', registrationNumber: 'R002' };
    const thirdStudent: TutorialGroupRegisterStudentRequest = { login: '', registrationNumber: 'R003' };
    const exampleRows: TutorialRegistrationsImportModalTableRow[] = [
        { login: 'user_1', registrationNumber: undefined, markFilledCells: false },
        { login: undefined, registrationNumber: 'ge86vox', markFilledCells: false },
    ];

    function createFileChangeEvent(file?: File): Event {
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: file ? [file] : [] });
        const event = new Event('change');
        Object.defineProperty(event, 'target', {
            value: input,
        });
        return event;
    }

    function expectExplanationStep() {
        const table = fixture.debugElement.query(By.directive(TutorialRegistrationsImportModalTableStubComponent))?.componentInstance ?? null;

        expect(component.flowStep()).toBe(ImportFlowStep.EXPLANATION);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.header()).toBe(
            'artemisApp.pages.tutorialGroupRegistrations.importModal.explanationHeader',
        );
        expect(fixture.nativeElement.querySelector('[data-testid="choose-file-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="back-to-explanation-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="import-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="restart-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="finish-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('.success-content')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="explanation-description"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="confirmation-description"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-all-registered"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-some-registered"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-none-registered"]')).toBeNull();
        expect(table).not.toBeNull();
        expect(table?.rows()).toEqual(exampleRows);
    }

    function expectConfirmationStep(expectedRows: TutorialRegistrationsImportModalTableRow[]) {
        const table = fixture.debugElement.query(By.directive(TutorialRegistrationsImportModalTableStubComponent))?.componentInstance ?? null;

        expect(component.flowStep()).toBe(ImportFlowStep.CONFIRMATION);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.header()).toBe(
            'artemisApp.pages.tutorialGroupRegistrations.importModal.confirmImportHeader',
        );
        expect(fixture.nativeElement.querySelector('[data-testid="choose-file-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="back-to-explanation-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="import-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="restart-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="finish-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('.success-content')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="explanation-description"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="confirmation-description"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-all-registered"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-some-registered"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-none-registered"]')).toBeNull();
        expect(table).not.toBeNull();
        expect(table?.rows()).toEqual(expectedRows);
    }

    function expectResultsStep(resultCase: ResultsStepCase, expectedRows?: TutorialRegistrationsImportModalTableRow[]) {
        const table = fixture.debugElement.query(By.directive(TutorialRegistrationsImportModalTableStubComponent))?.componentInstance ?? null;
        expect(component.flowStep()).toBe(ImportFlowStep.RESULTS);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.header()).toBe(
            'artemisApp.pages.tutorialGroupRegistrations.importModal.importResultsHeader',
        );
        expect(fixture.nativeElement.querySelector('[data-testid="finish-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="choose-file-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="back-to-explanation-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="import-button"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="explanation-description"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="confirmation-description"]')).toBeNull();
        if (resultCase === ResultsStepCase.ALL_REGISTERED) {
            expect(fixture.nativeElement.querySelector('.success-content')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="restart-button"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="results-description-all-registered"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="results-description-some-registered"]')).toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="results-description-none-registered"]')).toBeNull();
            expect(table).toBeNull();
            return;
        }

        expect(fixture.nativeElement.querySelector('.success-content')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="restart-button"]')).not.toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-all-registered"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-some-registered"]')).toBe(
            resultCase === ResultsStepCase.SOME_REGISTERED ? fixture.nativeElement.querySelector('[data-testid="results-description-some-registered"]') : null,
        );
        expect(fixture.nativeElement.querySelector('[data-testid="results-description-none-registered"]')).toBe(
            resultCase === ResultsStepCase.NONE_REGISTERED ? fixture.nativeElement.querySelector('[data-testid="results-description-none-registered"]') : null,
        );
        expect(table).not.toBeNull();
        expect(table?.rows()).toEqual(expectedRows);
    }

    beforeEach(async () => {
        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        tutorialGroupApiServiceMock = {
            importRegistrations: vi.fn(),
        };

        tutorialGroupRegisteredStudentsServiceMock = {
            fetchRegisteredStudents: vi.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [TutorialRegistrationsImportModalComponent],
            providers: [
                { provide: AlertService, useValue: alertServiceMock },
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiServiceMock },
                { provide: TutorialGroupRegisteredStudentsService, useValue: tutorialGroupRegisteredStudentsServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(TutorialRegistrationsImportModalComponent, {
                remove: {
                    imports: [DialogModule, TutorialRegistrationsImportModalTableComponent, LoadingIndicatorOverlayComponent],
                },
                add: {
                    imports: [PrimeNgDialogStubComponent, TutorialRegistrationsImportModalTableStubComponent, LoadingIndicatorOverlayStubComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TutorialRegistrationsImportModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('tutorialGroupId', 11);
        fixture.detectChanges();
        await fixture.whenStable();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render the explanation step when opened and move to confirmation after parsing a valid file', async () => {
        const parseSpy = vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent, secondParsedStudent],
        });
        const file = new File(['login'], 'students.csv', { type: 'text/csv' });

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const dialog = fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance;

        expect(dialog.visible()).toBe(true);
        expectExplanationStep();

        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(parseSpy).toHaveBeenCalledWith(file);
        expect(component.isLoading()).toBe(false);
        expectConfirmationStep([
            { login: 'ada', registrationNumber: 'R001', markFilledCells: false },
            { login: 'alan', registrationNumber: 'R002', markFilledCells: false },
        ]);
    });

    it('should stay on the explanation step and stop loading when no file is selected', async () => {
        const parseSpy = vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile');

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        await component.parseStudents(createFileChangeEvent());
        fixture.detectChanges();
        await fixture.whenStable();

        expect(parseSpy).not.toHaveBeenCalled();
        expect(alertServiceMock.addErrorAlert).not.toHaveBeenCalled();
        expect(component.isLoading()).toBe(false);
        expectExplanationStep();
    });

    it('should stay on the explanation step and stop loading when parsing yields invalid entries', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: false,
            invalidRowIndices: [2],
        });

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.importModal.invalidFileEntriesAlert');
        expect(component.isLoading()).toBe(false);
        expectExplanationStep();
    });

    it('should stay on the explanation step, show an error, and stop loading when parsing fails unexpectedly', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockRejectedValue(new Error('parse failed'));

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.importModal.importErrorAlert');
        expect(component.isLoading()).toBe(false);
        expectExplanationStep();
        expect(fixture.nativeElement.querySelector('jhi-loading-indicator-overlay')).toBeNull();
    });

    it('should stay on the explanation step and stop loading when parsing yields no entries', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [],
        });

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.importModal.noFileEntriesAlert');
        expect(component.isLoading()).toBe(false);
        expectExplanationStep();
        expect(fixture.nativeElement.querySelector('jhi-loading-indicator-overlay')).toBeNull();
    });

    it('should return from confirmation to the explanation step when the back button is clicked', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent],
        });

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="back-to-explanation-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expectExplanationStep();
    });

    it('should close the modal when finish is clicked in the results step', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent, secondParsedStudent],
        });
        tutorialGroupApiServiceMock.importRegistrations.mockReturnValue(of(new HttpResponse({ status: 200, body: [] })));

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="import-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="finish-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.isOpen()).toBe(false);
        expect(fixture.debugElement.query(By.directive(PrimeNgDialogStubComponent)).componentInstance.visible()).toBe(false);
    });

    it('should import parsed students and show the positive results state when all students are registered', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent, secondParsedStudent],
        });
        const response$ = new Subject<HttpResponse<TutorialGroupRegisterStudentRequest[]>>();
        tutorialGroupApiServiceMock.importRegistrations.mockReturnValue(response$.asObservable());

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="import-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(tutorialGroupApiServiceMock.importRegistrations).toHaveBeenCalledWith(7, 11, [firstStudent, secondStudent], 'response');
        expect(component.isLoading()).toBe(true);
        expect(fixture.nativeElement.querySelector('jhi-loading-indicator-overlay')).not.toBeNull();

        response$.next(new HttpResponse({ status: 200, body: [] }));
        response$.complete();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.allStudentsExist()).toBe(true);
        expect(component.noStudentsExist()).toBe(false);
        expect(tutorialGroupRegisteredStudentsServiceMock.fetchRegisteredStudents).toHaveBeenCalledWith(7, 11);
        expectResultsStep(ResultsStepCase.ALL_REGISTERED);
        expect(fixture.nativeElement.querySelector('jhi-loading-indicator-overlay')).toBeNull();
    });

    it('should show the negative results state and allow restarting when some students are not registered', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent, secondParsedStudent, thirdParsedStudent],
        });
        tutorialGroupApiServiceMock.importRegistrations.mockReturnValue(of(new HttpResponse({ status: 200, body: [secondStudent, thirdStudent] })));

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="import-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.allStudentsExist()).toBe(false);
        expect(component.noStudentsExist()).toBe(false);
        expect(component.isLoading()).toBe(false);
        expect(tutorialGroupRegisteredStudentsServiceMock.fetchRegisteredStudents).toHaveBeenCalledWith(7, 11);
        expectResultsStep(ResultsStepCase.SOME_REGISTERED, [
            { login: 'ada', registrationNumber: 'R001', markFilledCells: false },
            { login: 'alan', registrationNumber: 'R002', markFilledCells: true },
            { login: '', registrationNumber: 'R003', markFilledCells: true },
        ]);

        fixture.nativeElement.querySelector('[data-testid="restart-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expectExplanationStep();
    });

    it('should show an error and stay on the confirmation step when importing students fails', async () => {
        vi.spyOn(readUsersFromCsv, 'readStudentDTOsFromCSVFile').mockResolvedValue({
            ok: true,
            students: [firstParsedStudent, secondParsedStudent],
        });
        tutorialGroupApiServiceMock.importRegistrations.mockReturnValue(throwError(() => new Error('import failed')));

        component.open();
        fixture.detectChanges();
        await fixture.whenStable();

        const file = new File(['login'], 'students.csv', { type: 'text/csv' });
        await component.parseStudents(createFileChangeEvent(file));
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.nativeElement.querySelector('[data-testid="import-button"]').click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceMock.addErrorAlert).toHaveBeenCalledWith('artemisApp.pages.tutorialGroupRegistrations.importModal.importErrorAlert');
        expect(component.isLoading()).toBe(false);
        expect(tutorialGroupRegisteredStudentsServiceMock.fetchRegisteredStudents).not.toHaveBeenCalled();
        expectConfirmationStep([
            { login: 'ada', registrationNumber: 'R001', markFilledCells: false },
            { login: 'alan', registrationNumber: 'R002', markFilledCells: false },
        ]);
    });
});

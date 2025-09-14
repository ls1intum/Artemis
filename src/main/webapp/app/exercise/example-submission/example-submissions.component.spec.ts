import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { ExampleSubmissionsComponent } from 'app/exercise/example-submission/example-submissions.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ResultComponent } from 'app/exercise/result/result.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { AlertService } from 'app/shared/service/alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ExampleSubmissionDTO } from 'app/assessment/shared/entities/example-submission.model';

describe('Example Submission Component', () => {
    let component: ExampleSubmissionsComponent;
    let fixture: ComponentFixture<ExampleSubmissionsComponent>;
    let exampleSubmissionService: ExampleSubmissionService;
    let modalService: NgbModal;
    let alertService: AlertService;

    const exampleSubmission1 = { id: 1 } as ExampleSubmission;
    const exampleSubmission2 = { id: 2 } as ExampleSubmission;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        exampleSubmissions: [exampleSubmission1, exampleSubmission2],
    };

    const route = { data: of({ exercise }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterTestingModule],
            declarations: [ExampleSubmissionsComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective), MockComponent(ResultComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                MockProvider(NgbModal),
                MockProvider(AlertService),
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionsComponent);
                component = fixture.componentInstance;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                modalService = TestBed.inject(NgbModal);
                alertService = TestBed.inject(AlertService);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize the component', () => {
        component.ngOnInit();

        expect(component.exercise).toBeDefined();
    });

    it('should delete an example submission', () => {
        // GIVEN
        component.exercise = exercise;
        const deleteStub = jest.spyOn(exampleSubmissionService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        // WHEN
        component.deleteExampleSubmission(0);

        // THEN
        expect(deleteStub).toHaveBeenCalledOnce();
        expect(exercise.exampleSubmissions).toHaveLength(1);
    });

    it('should catch an error on delete', () => {
        component.exercise = exercise;
        jest.spyOn(exampleSubmissionService, 'delete').mockReturnValue(throwError(() => ({ status: 500 })));

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.deleteExampleSubmission(0);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should get the submission size', () => {
        const textSubmission = {
            id: 2,
            text: 'test text',
        } as TextSubmission;
        const exampleTextSubmission = {
            id: 1,
            submission: textSubmission,
        } as ExampleSubmission;
        exercise.exampleSubmissions = [exampleTextSubmission];

        const getSubmissionSizeSpy = jest.spyOn(exampleSubmissionService, 'getSubmissionSize');

        component.exercise = exercise;
        component.ngOnInit();
        expect(component.exercise.exampleSubmissions).toBeDefined();
        expect(component.exercise.exampleSubmissions![0].submission?.submissionSize).toBe(2);
        expect(getSubmissionSizeSpy).toHaveBeenCalledOnce();
    });

    it('should not open import modal', () => {
        component.exercise = exercise;
        const componentInstance = { exercise: Exercise };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        const importStub = jest.spyOn(exampleSubmissionService, 'import').mockReturnValue(throwError(() => ({ status: 500 })));

        component.openImportModal();

        expect(modalServiceStub).toHaveBeenCalledOnce();
        expect(importStub).not.toHaveBeenCalled();
    });

    it('should close open modals on component destroy', () => {
        const modalServiceStub = jest.spyOn(modalService, 'hasOpenModals').mockReturnValue(true);
        const modalServiceDismissSpy = jest.spyOn(modalService, 'dismissAll');

        component.ngOnDestroy();

        expect(modalServiceStub).toHaveBeenCalledOnce();
        expect(modalServiceDismissSpy).toHaveBeenCalledOnce();
    });
});
//DTO testing
describe('ExampleSubmissionDTO', () => {
    it('should construct with values', () => {
        const dto = new ExampleSubmissionDTO(7, true, 'hello world');
        expect(dto.id).toBe(7);
        expect(dto.usedForTutorial).toBeTrue();
        expect(dto.assessmentExplanation).toBe('hello world');
    });
});

import { ArtemisTestModule } from '../../../test.module';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { ExampleSubmissionsComponent } from 'app/exercises/shared/example-submission/example-submissions.component';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockDirective, MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { ResultComponent } from 'app/exercises/shared/result/result.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { TextSubmission } from 'app/entities/text-submission.model';
import { AlertService } from 'app/core/util/alert.service';

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
            imports: [ArtemisTestModule, MockModule(RouterModule)],
            declarations: [
                ExampleSubmissionsComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(TranslateDirective),
                MockComponent(ResultComponent),
                MockComponent(AlertComponent),
                MockComponent(AlertErrorComponent),
                MockDirective(NgbTooltip),
            ],
            providers: [{ provide: ActivatedRoute, useValue: route }, { provide: TranslateService, useClass: MockTranslateService }, MockProvider(NgbModal)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleSubmissionsComponent);
                component = fixture.componentInstance;
                exampleSubmissionService = TestBed.inject(ExampleSubmissionService);
                modalService = fixture.debugElement.injector.get(NgbModal);
                alertService = fixture.debugElement.injector.get(AlertService);
            });
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should initialize the component', () => {
        component.ngOnInit();

        expect(component.exercise).not.toBe(undefined);
    });

    it('should delete an example submission', () => {
        // GIVEN
        component.exercise = exercise;
        const deleteStub = jest.spyOn(exampleSubmissionService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        // WHEN
        component.deleteExampleSubmission(0);

        // THEN
        expect(deleteStub).toHaveBeenCalledTimes(1);
        expect(exercise.exampleSubmissions?.length).toBe(1);
    });

    it('should catch an error on delete', () => {
        component.exercise = exercise;
        jest.spyOn(exampleSubmissionService, 'delete').mockReturnValue(throwError({ status: 500 }));

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.deleteExampleSubmission(0);

        expect(alertServiceSpy).toHaveBeenCalledTimes(1);
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
        expect(component.exercise.exampleSubmissions).not.toBe(undefined);
        expect(component.exercise.exampleSubmissions![0].submission?.submissionSize).toBe(2);
        expect(getSubmissionSizeSpy).toHaveBeenCalledTimes(1);
    });

    it('should not open import modal', () => {
        component.exercise = exercise;
        const componentInstance = { exercise: Exercise };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        const importStub = jest.spyOn(exampleSubmissionService, 'import').mockReturnValue(throwError({ status: 500 }));

        component.openImportModal();

        expect(modalServiceStub).toHaveBeenCalledTimes(1);
        expect(importStub).not.toHaveBeenCalled();
    });

    it('should close open modals on component destroy', () => {
        const modalServiceStub = jest.spyOn(modalService, 'hasOpenModals').mockReturnValue(true);
        const modalServiceDismissSpy = jest.spyOn(modalService, 'dismissAll');

        component.ngOnDestroy();

        expect(modalServiceStub).toHaveBeenCalledTimes(1);
        expect(modalServiceDismissSpy).toHaveBeenCalledTimes(1);
    });
});

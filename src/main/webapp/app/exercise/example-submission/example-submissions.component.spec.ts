import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExampleParticipation } from 'app/exercise/shared/entities/participation/example-participation.model';
import { ExampleSubmissionsComponent } from 'app/exercise/example-submission/example-submissions.component';
import { ExampleParticipationService } from 'app/assessment/shared/services/example-participation.service';
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

describe('Example Submission Component', () => {
    let component: ExampleSubmissionsComponent;
    let fixture: ComponentFixture<ExampleSubmissionsComponent>;
    let exampleParticipationService: ExampleParticipationService;
    let modalService: NgbModal;
    let alertService: AlertService;

    const exampleParticipation1 = { id: 1 } as ExampleParticipation;
    const exampleParticipation2 = { id: 2 } as ExampleParticipation;

    const exercise: Exercise = {
        id: 1,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
        exampleParticipations: [exampleParticipation1, exampleParticipation2],
    };

    const route = { data: of({ exercise }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
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
                exampleParticipationService = TestBed.inject(ExampleParticipationService);
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

    it('should delete an example participation', () => {
        // GIVEN
        component.exercise = exercise;
        const deleteStub = jest.spyOn(exampleParticipationService, 'delete').mockReturnValue(of(new HttpResponse<void>()));

        // WHEN
        component.deleteExampleParticipation(0);

        // THEN
        expect(deleteStub).toHaveBeenCalledOnce();
        expect(exercise.exampleParticipations).toHaveLength(1);
    });

    it('should catch an error on delete', () => {
        component.exercise = exercise;
        jest.spyOn(exampleParticipationService, 'delete').mockReturnValue(throwError(() => ({ status: 500 })));

        const alertServiceSpy = jest.spyOn(alertService, 'error');
        component.deleteExampleParticipation(0);

        expect(alertServiceSpy).toHaveBeenCalledOnce();
    });

    it('should get the submission size', () => {
        const textSubmission = {
            id: 2,
            text: 'test text',
        } as TextSubmission;
        const exampleTextParticipation = {
            id: 1,
            submissions: [textSubmission],
        } as ExampleParticipation;
        exercise.exampleParticipations = [exampleTextParticipation];

        const getSubmissionSizeSpy = jest.spyOn(exampleParticipationService, 'getSubmissionSize');

        component.exercise = exercise;
        component.ngOnInit();
        expect(component.exercise.exampleParticipations).toBeDefined();
        expect(component.getSubmission(component.exercise.exampleParticipations![0])?.submissionSize).toBe(2);
        expect(getSubmissionSizeSpy).toHaveBeenCalledOnce();
    });

    it('should not open import modal', () => {
        component.exercise = exercise;
        const componentInstance = { exercise: Exercise };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceStub = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{ componentInstance, result });
        const importStub = jest.spyOn(exampleParticipationService, 'import').mockReturnValue(throwError(() => ({ status: 500 })));

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

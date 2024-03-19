import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { MockProgrammingExerciseParticipationService } from '../../helpers/mocks/service/mock-programming-exercise-participation.service';
import { MockProgrammingExerciseService } from '../../helpers/mocks/service/mock-programming-exercise.service';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { RepositoryViewComponent } from 'app/localvc/repository-view/repository-view.component';
import { AccountService } from 'app/core/auth/account.service';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';

describe('RepositoryViewComponent', () => {
    let component: RepositoryViewComponent;
    let fixture: ComponentFixture<RepositoryViewComponent>;
    let mockDomainService: Partial<DomainService>;
    let activatedRoute: MockActivatedRoute;
    let programmingExerciseService: ProgrammingExerciseService;
    let programmingExerciseParticipationService: ProgrammingExerciseParticipationService;

    beforeEach(async () => {
        mockDomainService = {
            setDomain: jest.fn(),
        };

        await TestBed.configureTestingModule({
            declarations: [RepositoryViewComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: DomainService, useValue: mockDomainService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: Router, useClass: MockRouter },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RepositoryViewComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                activatedRoute = fixture.debugElement.injector.get(ActivatedRoute) as MockActivatedRoute;
                programmingExerciseService = fixture.debugElement.injector.get(ProgrammingExerciseService);
                programmingExerciseParticipationService = fixture.debugElement.injector.get(ProgrammingExerciseParticipationService);
            });
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load participation for TEMPLATE repository type', () => {
        // Mock exercise and participation data
        const mockExercise: ProgrammingExercise = {
            id: 1,
            templateParticipation: { id: 2, repositoryUri: 'template-repo-uri' },
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: true,
            secondCorrectionEnabled: true,
        };
        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExercise });
        const exerciseId = 1;

        activatedRoute.setParameters({ exerciseId: exerciseId, repositoryType: 'TEMPLATE' });
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(of(mockExerciseResponse));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect exercise and participation to be set correctly
        expect(component.exercise).toEqual(mockExercise);
        expect(component.participation).toEqual(mockExercise.templateParticipation);

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.PARTICIPATION, mockExercise.templateParticipation]);
        expect(component.repositoryUri).toBe('template-repo-uri');

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load participation for SOLUTION repository type', () => {
        // Mock exercise and participation data
        const mockExercise: ProgrammingExercise = {
            id: 1,
            solutionParticipation: { id: 2, repositoryUri: 'solution-repo-uri' },
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: true,
            secondCorrectionEnabled: true,
        };
        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExercise });
        const exerciseId = 1;

        activatedRoute.setParameters({ exerciseId: exerciseId, repositoryType: 'SOLUTION' });
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(of(mockExerciseResponse));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect exercise and participation to be set correctly
        expect(component.exercise).toEqual(mockExercise);
        expect(component.participation).toEqual(mockExercise.solutionParticipation);

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.PARTICIPATION, mockExercise.solutionParticipation]);
        expect(component.repositoryUri).toBe('solution-repo-uri');

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load participation for TESTS repository type', () => {
        // Mock exercise and participation data
        const mockExercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: true,
            secondCorrectionEnabled: true,
        };
        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExercise });
        const exerciseId = 1;

        activatedRoute.setParameters({ exerciseId: exerciseId, repositoryType: 'TESTS' });
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(of(mockExerciseResponse));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect exercise and participation to be set correctly
        expect(component.exercise).toEqual(mockExercise);
        expect(component.participation).toBeUndefined();

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.TEST_REPOSITORY, mockExercise]);
        expect(component.repositoryUri).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle unknown repository type', () => {
        // Mock exercise and participation data
        const mockExercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            studentAssignedTeamIdComputed: true,
            secondCorrectionEnabled: true,
        };
        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExercise });
        const exerciseId = 1;

        // route to an unknown repository type
        activatedRoute.setParameters({ exerciseId: exerciseId, repositoryType: 'UNKNOWN' });

        // Mock the service to return an error
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(of(mockExerciseResponse));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect participationCouldNotBeFetched to be true
        expect(component.participationCouldNotBeFetched).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load student participation', () => {
        // Mock participation data
        const mockParticipation: ProgrammingExerciseStudentParticipation = {
            id: 2,
            repositoryUri: 'student-repo-uri',
            exercise: { id: 1, numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()], studentAssignedTeamIdComputed: true, secondCorrectionEnabled: true },
            results: [
                { id: 3, successful: true, score: 100, rated: true, hasComplaint: false, exampleResult: false, testCaseCount: 10, passedTestCaseCount: 10, codeIssueCount: 0 },
                { id: 4, successful: true, score: 100, rated: true, hasComplaint: false, exampleResult: false, testCaseCount: 10, passedTestCaseCount: 10, codeIssueCount: 0 },
            ],
        };
        const participationId = 2;

        activatedRoute.setParameters({ participationId: participationId });
        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(of(mockParticipation));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect exercise and participation to be set correctly
        expect(component.exercise).toEqual(mockParticipation.exercise);
        expect(component.participation).toEqual(mockParticipation);

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.PARTICIPATION, mockParticipation]);
        expect(component.repositoryUri).toBe('student-repo-uri');

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.participationWithLatestResultSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle error when loading participation', () => {
        // route to a participation that does not exist
        activatedRoute.setParameters({ participationId: 8 });

        // Mock the service to return an error
        jest.spyOn(programmingExerciseParticipationService, 'getStudentParticipationWithLatestResult').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.error('Error');
            }),
        );

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect participationCouldNotBeFetched to be true
        expect(component.participationCouldNotBeFetched).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.participationWithLatestResultSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should handle error when loading exercise', () => {
        // route to an exercise that does not exist
        activatedRoute.setParameters({ exerciseId: 8 });

        // Mock the service to return an error
        jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipationAndLatestResults').mockReturnValue(
            new Observable((subscriber) => {
                subscriber.error('Error');
            }),
        );

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect participationCouldNotBeFetched to be true
        expect(component.participationCouldNotBeFetched).toBeTrue();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { RepositoryViewComponent } from 'app/programming/shared/repository-view/repository-view.component';
import { AccountService } from 'app/core/auth/account.service';
import { DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { Observable, of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

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
            subscribeDomainChange: jest.fn().mockReturnValue(of(null)),
        };

        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: DomainService, useValue: mockDomainService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute({ key: 'ABC123' }) },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(RepositoryViewComponent);
                component = fixture.componentInstance;
                fixture.detectChanges();

                activatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                programmingExerciseParticipationService = TestBed.inject(ProgrammingExerciseParticipationService);
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
        expect(component.participation).toEqual({});

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.TEST_REPOSITORY, mockExercise]);
        expect(component.repositoryUri).toBeUndefined();

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
        expect(component.differentParticipationSub?.closed).toBeTrue();
        expect(component.paramSub?.closed).toBeTrue();
    });

    it('should load AUXILIARY repository type', () => {
        // Mock exercise and participation data
        const mockAuxiliaryRepository: AuxiliaryRepository = { id: 5, repositoryUri: 'repositoryUri', checkoutDirectory: 'dir', name: 'AuxRepo', description: 'description' };
        const mockExercise: ProgrammingExercise = {
            id: 1,
            numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
            auxiliaryRepositories: [mockAuxiliaryRepository],
            studentAssignedTeamIdComputed: true,
            secondCorrectionEnabled: true,
        };
        const mockExerciseResponse: HttpResponse<ProgrammingExercise> = new HttpResponse({ body: mockExercise });
        const exerciseId = 1;
        const auxiliaryRepositoryId = 5;

        activatedRoute.setParameters({ exerciseId: exerciseId, repositoryType: 'AUXILIARY', repositoryId: auxiliaryRepositoryId });
        jest.spyOn(programmingExerciseService, 'findWithAuxiliaryRepository').mockReturnValue(of(mockExerciseResponse));

        // Trigger ngOnInit
        component.ngOnInit();

        // Expect loadingParticipation to be false after loading
        expect(component.loadingParticipation).toBeFalse();

        // Expect exercise and participation to be set correctly
        expect(component.exercise).toEqual(mockExercise);
        expect(component.participation).toEqual({});

        // Expect domainService method to be called with the correct arguments
        expect(component.domainService.setDomain).toHaveBeenCalledWith([DomainType.AUXILIARY_REPOSITORY, mockAuxiliaryRepository]);

        // Trigger ngOnDestroy
        component.ngOnDestroy();

        // Expect subscription to be unsubscribed
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
            exercise: {
                id: 1,
                numberOfAssessmentsOfCorrectionRounds: [new DueDateStat()],
                studentAssignedTeamIdComputed: true,
                secondCorrectionEnabled: true,
                course: {
                    instructorGroupName: 'instructorGroup',
                    isAtLeastInstructor: true,
                },
            },
            submissions: [
                {
                    results: [
                        {
                            id: 3,
                            successful: true,
                            score: 100,
                            rated: true,
                            hasComplaint: false,
                            exampleResult: false,
                            testCaseCount: 10,
                            passedTestCaseCount: 10,
                            codeIssueCount: 0,
                        },
                        {
                            id: 4,
                            successful: true,
                            score: 100,
                            rated: true,
                            hasComplaint: false,
                            exampleResult: false,
                            testCaseCount: 10,
                            passedTestCaseCount: 10,
                            codeIssueCount: 0,
                        },
                    ],
                },
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
        expect(component.paramSub?.closed).toBeTrue();
    });
});

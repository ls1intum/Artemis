import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExerciseProblemComponent } from 'app/programming/manage/update/update-components/problem/programming-exercise-problem.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { programmingExerciseCreationConfigMock } from 'test/helpers/mocks/programming-exercise-creation-config-mock';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ProblemStatementGenerationResponse } from 'app/openapi/model/problemStatementGenerationResponse';
import { AlertService } from 'app/shared/service/alert.service';
import { ProblemStatementGenerationRequest } from 'app/openapi/model/problemStatementGenerationRequest';

describe('ProgrammingExerciseProblemComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseProblemComponent>;
    let comp: ProgrammingExerciseProblemComponent;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
        queryParams: of({}),
    } as ActivatedRoute;

    const mockHyperionApiService = {
        generateProblemStatement: jest.fn(),
    };

    const mockAlertService = {
        success: jest.fn(),
        error: jest.fn(),
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: HyperionProblemStatementApiService, useValue: mockHyperionApiService },
                { provide: AlertService, useValue: mockAlertService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseProblemComponent);
        comp = fixture.componentInstance;

        fixture.componentRef.setInput('isEditFieldDisplayedRecord', {
            problemStatement: true,
            linkedCompetencies: true,
        });

        fixture.componentRef.setInput('programmingExercise', new ProgrammingExercise(undefined, undefined));
        fixture.componentRef.setInput('programmingExerciseCreationConfig', programmingExerciseCreationConfigMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize and store exercise', fakeAsync(() => {
        fixture.detectChanges();
        expect(comp).not.toBeNull();

        const exercise = comp.programmingExercise();
        expect(exercise).toBeDefined();
    }));

    it('should generate problem statement successfully', fakeAsync(() => {
        const courseId = 42;
        const userPrompt = 'Create a Java exercise about binary search trees';
        const generatedText = 'Generated draft problem statement about binary search trees';

        const mockResponse: ProblemStatementGenerationResponse = {
            draftProblemStatement: generatedText,
        };

        const request: ProblemStatementGenerationRequest = {
            userPrompt: userPrompt,
        };

        // Set up the programming exercise with a course
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: courseId } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse));

        // Trigger the generation
        comp.userPrompt = userPrompt;
        comp.generateProblemStatement();

        // Verify the API was called correctly
        expect(mockHyperionApiService.generateProblemStatement).toHaveBeenCalledWith(courseId, request);

        // Verify the alert was shown
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationSuccess');

        // Verify the exercise was updated
        expect(programmingExercise.problemStatement).toBe(generatedText);
    }));

    it('should not call API when prompt or course is missing', () => {
        mockHyperionApiService.generateProblemStatement.mockClear();
        comp.userPrompt = '   ';
        comp.generateProblemStatement();
        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();

        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        comp.userPrompt = 'prompt';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        mockHyperionApiService.generateProblemStatement.mockClear();
        comp.generateProblemStatement();
        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('shows error when generated problem statement is empty', fakeAsync(() => {
        const courseId = 21;
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: courseId } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt = 'valid';
        mockHyperionApiService.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '   ' }));

        comp.generateProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
        expect(comp.userPrompt).toBe('valid');
        expect(comp.isGenerating).toBeFalse();
    }));

    it('shows error when generation fails', fakeAsync(() => {
        const courseId = 31;
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: courseId } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt = 'cause error';
        mockHyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('failure')));

        comp.generateProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
        expect(comp.isGenerating).toBeFalse();
    }));

    it('cancels generation and unsubscribes', () => {
        comp.isGenerating = true;
        comp['currentGenerationSubscription'] = of().subscribe();
        const unsubscribeSpy = jest.spyOn(comp['currentGenerationSubscription']!, 'unsubscribe');

        comp.cancelGeneration();

        expect(unsubscribeSpy).toHaveBeenCalled();
        expect(comp.isGenerating).toBeFalse();
    });

    it('cleans up subscription on destroy', () => {
        comp['currentGenerationSubscription'] = of().subscribe();
        const unsubscribeSpy = jest.spyOn(comp['currentGenerationSubscription']!, 'unsubscribe');

        comp.ngOnDestroy();

        expect(unsubscribeSpy).toHaveBeenCalled();
    });
});

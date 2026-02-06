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
import { ProblemStatementRefinementResponse } from 'app/openapi/model/problemStatementRefinementResponse';

describe('ProgrammingExerciseProblemComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseProblemComponent>;
    let comp: ProgrammingExerciseProblemComponent;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
        queryParams: of({}),
    } as ActivatedRoute;

    const mockHyperionApiService = {
        generateProblemStatement: jest.fn(),
        refineProblemStatementGlobally: jest.fn(),
        refineProblemStatementTargeted: jest.fn(),
    };

    const mockAlertService = {
        success: jest.fn(),
        error: jest.fn(),
        warning: jest.fn(),
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
        jest.clearAllMocks();
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
        comp.userPrompt.set(userPrompt);
        comp.generateProblemStatement();

        // Verify the API was called correctly
        expect(mockHyperionApiService.generateProblemStatement).toHaveBeenCalledWith(courseId, request);

        // Verify the alert was shown
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationSuccess');

        // Verify the exercise was updated
        expect(programmingExercise.problemStatement).toBe(generatedText);
    }));

    it('should not generate when userPrompt is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('');
        comp.generateProblemStatement();

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should not generate when no course id', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        // No course set
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('Test prompt');
        comp.generateProblemStatement();

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should handle generation error', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('API error')));

        comp.userPrompt.set('Test prompt');
        comp.generateProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
        expect(comp.isGeneratingOrRefining()).toBeFalse();
    }));

    it('should handle empty generation response', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }));

        comp.userPrompt.set('Test prompt');
        comp.generateProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
    }));

    it('should refine problem statement successfully', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original problem statement';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = {
            refinedProblemStatement: 'Refined problem statement',
        };

        mockHyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Improve clarity');
        comp.refineProblemStatement();

        expect(comp.showDiff()).toBeTrue();
    }));

    it('should accept refinement and update problem statement', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.showDiff.set(true);

        comp.closeDiffView();

        expect(comp.showDiff()).toBeFalse();
    });

    it('should revert refinement and close diff', () => {
        comp.showDiff.set(true);
        // Mock editableInstructions
        (comp as any).editableInstructions = () => ({
            revertAll: jest.fn(),
            getCurrentContent: jest.fn().mockReturnValue('Reverted content'),
        });

        comp.revertAllChanges();

        expect(comp.showDiff()).toBeFalse();
    });

    it('should cancel generation and reset states', () => {
        comp.isGeneratingOrRefining.set(true);

        comp.cancelGeneration();

        expect(comp.isGeneratingOrRefining()).toBeFalse();
    });

    it('should handle onProblemStatementChange', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        const emitSpy = jest.spyOn(comp.problemStatementChange, 'emit');

        comp.onProblemStatementChange('New problem statement');

        expect(emitSpy).toHaveBeenCalledWith('New problem statement');
        expect(programmingExercise.problemStatement).toBe('New problem statement');
    });

    it('should handle competency links change', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const emitSpy = jest.spyOn(comp.programmingExerciseChange, 'emit');

        comp.onCompetencyLinksChange([{ id: 1 }] as any);

        expect(programmingExercise.competencyLinks).toEqual([{ id: 1 }]);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should handle handleProblemStatementAction for generate', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = ''; // Empty, should trigger generate
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const generateSpy = jest.spyOn(comp, 'generateProblemStatement');

        comp.userPrompt.set('Test');
        comp.handleProblemStatementAction();

        expect(generateSpy).toHaveBeenCalled();
    });

    it('should handle handleProblemStatementAction for refine', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Existing problem statement'; // Non-empty, should trigger refine
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges(); // Trigger ngOnInit to set currentProblemStatement signal

        // Set templateLoaded so shouldShowGenerateButton returns false for non-template content
        comp['templateLoaded'].set(true);
        comp['templateProblemStatement'].set('Different template');

        const refineSpy = jest.spyOn(comp, 'refineProblemStatement');

        comp.userPrompt.set('Improve this');
        comp.handleProblemStatementAction();

        expect(refineSpy).toHaveBeenCalled();
    });

    it('should return translated placeholder', () => {
        const translateService = TestBed.inject(TranslateService);
        translateService.instant = jest.fn().mockReturnValue('Translated placeholder');

        const result = comp.getTranslatedPlaceholder();

        expect(result).toBe('Translated placeholder');
        expect(translateService.instant).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.examplePlaceholder');
    });

    it('should accept refinement and apply changes', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', exercise);

        comp.showDiff.set(true);

        comp.closeDiffView();

        expect(comp.showDiff()).toBeFalse();
    });

    it('should handle generate with existing non-empty problem statement', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.course = { id: 42 } as any;
        exercise.problemStatement = 'Existing statement';
        fixture.componentRef.setInput('programmingExercise', exercise);
        fixture.detectChanges();

        // Set templateLoaded so shouldShowGenerateButton compares against template
        comp['templateLoaded'].set(true);
        comp['templateProblemStatement'].set('Different template');

        // shouldShowGenerateButton should be false for existing content that differs from template
        expect(comp.shouldShowGenerateButton()).toBeFalse();
    });

    it('should reset generation state on cancel', () => {
        // Set up state
        comp.isGeneratingOrRefining.set(true);

        comp.cancelGeneration();

        // Verify state is reset
        expect(comp.isGeneratingOrRefining()).toBeFalse();
    });

    it('should handle inline refinement successfully', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original problem statement with content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = {
            refinedProblemStatement: 'Refined problem statement',
        };

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(of(mockResponse));

        const event = {
            instruction: 'Improve this section',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockHyperionApiService.refineProblemStatementTargeted).toHaveBeenCalledWith(
            42,
            expect.objectContaining({
                problemStatementText: 'Original problem statement with content',
                instruction: 'Improve this section',
                startLine: 1,
                endLine: 2,
                startColumn: 1,
                endColumn: 10,
            }),
        );

        expect(comp.showDiff()).toBeTrue();
        expect(mockAlertService.success).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefinement.success');
    }));

    it('should handle inline refinement error when no courseId', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Some content';
        // No course set
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        // Service returns silently with success: false when courseId is missing
        expect(mockHyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
    });

    it('should handle inline refinement error when problem statement is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = '   '; // Only whitespace
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        // Component validates empty content before calling service
        expect(mockHyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
    });

    it('should handle inline refinement API error', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(throwError(() => new Error('API error')));

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefinement.error');
        expect(comp.isGeneratingOrRefining()).toBeFalse();
    }));

    it('should handle inline refinement with empty response', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatementTargeted.mockReturnValue(of({ refinedProblemStatement: '' }));

        const event = {
            instruction: 'Improve this',
            startLine: 1,
            endLine: 2,
            startColumn: 1,
            endColumn: 10,
        };

        comp.onInlineRefinement(event);

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefinement.error');
    }));

    it('should handle refinement with completely empty response', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementRefinementResponse = { refinedProblemStatement: '' };

        mockHyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Improve clarity');
        comp.refineProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
    }));

    it('should not refine when userPrompt is empty', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('');
        comp.refineProblemStatement();

        expect(mockHyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should not refine when no courseId', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt.set('Improve');
        comp.refineProblemStatement();

        expect(mockHyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
    });

    it('should close diff view properly', () => {
        comp.showDiff.set(true);

        comp.closeDiffView();

        expect(comp.showDiff()).toBeFalse();
    });

    it('should use exerciseGroup course id when course is not set', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.exerciseGroup = { exam: { course: { id: 99 } } } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const mockResponse: ProblemStatementGenerationResponse = {
            draftProblemStatement: 'Generated statement',
        };

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse));

        comp.userPrompt.set('Create exercise');
        comp.generateProblemStatement();

        expect(mockHyperionApiService.generateProblemStatement).toHaveBeenCalledWith(99, expect.any(Object));
    }));

    it('should cancel active subscription on cancelGeneration', () => {
        const mockSubscription = { unsubscribe: jest.fn() };
        comp['currentGenerationSubscription'] = mockSubscription as any;

        comp.cancelGeneration();

        expect(mockSubscription.unsubscribe).toHaveBeenCalled();
        expect(comp['currentGenerationSubscription']).toBeUndefined();
    });

    it('should unsubscribe on destroy', () => {
        const mockSubscription = { unsubscribe: jest.fn() };
        comp['currentGenerationSubscription'] = mockSubscription as any;

        comp.ngOnDestroy();

        expect(mockSubscription.unsubscribe).toHaveBeenCalled();
    });

    it('should show generate button when problem statement matches template', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Template content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        // Set the template to match the problem statement
        comp['templateProblemStatement'].set('Template content');
        comp['templateLoaded'].set(true);
        comp['currentProblemStatement'].set('Template content');

        expect(comp.shouldShowGenerateButton()).toBeTrue();
    }));

    it('should show refine button when problem statement differs from template', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Custom content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        comp['templateProblemStatement'].set('Template content');
        comp['templateLoaded'].set(true);
        comp['currentProblemStatement'].set('Custom content');

        expect(comp.shouldShowGenerateButton()).toBeFalse();
    }));

    it('should show generate button when template loading fails', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.problemStatement = 'Existing content';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);
        fixture.detectChanges();

        comp['templateLoaded'].set(false);
        comp['currentProblemStatement'].set('Existing content');

        expect(comp.shouldShowGenerateButton()).toBeTrue();
    }));
});

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
import { InlineCommentService } from 'app/shared/monaco-editor/service/inline-comment.service';

describe('ProgrammingExerciseProblemComponent', () => {
    let fixture: ComponentFixture<ProgrammingExerciseProblemComponent>;
    let comp: ProgrammingExerciseProblemComponent;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: '1' }) },
        queryParams: of({}),
    } as ActivatedRoute;

    const mockHyperionApiService = {
        generateProblemStatement: jest.fn(),
        refineProblemStatement: jest.fn(),
    };

    const mockAlertService = {
        success: jest.fn(),
        error: jest.fn(),
        warning: jest.fn(),
    };

    const mockInlineCommentService = {
        getPendingComments: jest.fn(() => () => []),
        updateStatus: jest.fn(),
        markApplied: jest.fn(),
        markAllApplied: jest.fn(),
        clearAll: jest.fn(),
        getComment: jest.fn(),
        addExistingComment: jest.fn(),
        removeComment: jest.fn(),
        setExerciseContext: jest.fn(),
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
                { provide: InlineCommentService, useValue: mockInlineCommentService },
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
        comp.userPrompt = userPrompt;
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

        comp.userPrompt = '';
        comp.generateProblemStatement();

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should not generate when no course id', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        // No course set
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.userPrompt = 'Test prompt';
        comp.generateProblemStatement();

        expect(mockHyperionApiService.generateProblemStatement).not.toHaveBeenCalled();
    });

    it('should handle generation error', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('API error')));

        comp.userPrompt = 'Test prompt';
        comp.generateProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.generationError');
        expect(comp.isGenerating()).toBeFalse();
    }));

    it('should handle empty generation response', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }));

        comp.userPrompt = 'Test prompt';
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

        mockHyperionApiService.refineProblemStatement.mockReturnValue(of(mockResponse));

        comp.userPrompt = 'Improve clarity';
        comp.refineProblemStatement();

        expect(comp.showDiff).toBeTrue();
        expect(comp.refinedProblemStatement).toBe('Refined problem statement');
        expect(comp.originalProblemStatement).toBe('Original problem statement');
    }));

    it('should handle refinement error', fakeAsync(() => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        mockHyperionApiService.refineProblemStatement.mockReturnValue(throwError(() => new Error('API error')));

        comp.userPrompt = 'Improve';
        comp.refineProblemStatement();

        expect(mockAlertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
        expect(comp.isRefining()).toBeFalse();
    }));

    it('should accept refinement and update problem statement', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = 'Original';
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        comp.showDiff = true;
        comp.refinedProblemStatement = 'Refined';

        comp.acceptRefinement();

        expect(programmingExercise.problemStatement).toBe('Refined');
        expect(comp.showDiff).toBeFalse();
    });

    it('should reject refinement and close diff', () => {
        comp.showDiff = true;
        comp.originalProblemStatement = 'Original';
        comp.refinedProblemStatement = 'Refined';

        comp.rejectRefinement();

        expect(comp.showDiff).toBeFalse();
        expect(comp.originalProblemStatement).toBe('');
        expect(comp.refinedProblemStatement).toBe('');
    });

    it('should close diff and reset state', () => {
        comp.showDiff = true;
        comp.originalProblemStatement = 'Original';
        comp.refinedProblemStatement = 'Refined';
        (comp as any).diffContentSet = true;

        comp.closeDiff();

        expect(comp.showDiff).toBeFalse();
        expect(comp.originalProblemStatement).toBe('');
        expect(comp.refinedProblemStatement).toBe('');
        expect((comp as any).diffContentSet).toBeFalse();
    });

    it('should cancel generation and reset states', () => {
        comp.isGenerating.set(true);
        comp.isRefining.set(true);

        comp.cancelGeneration();

        expect(comp.isGenerating()).toBeFalse();
        expect(comp.isRefining()).toBeFalse();
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

    it('should get translated placeholder', () => {
        const placeholder = comp.getTranslatedPlaceholder();
        expect(placeholder).toBeDefined();
    });

    it('should handle competency links change', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const emitSpy = jest.spyOn(comp.programmingExerciseChange, 'emit');

        comp.onCompetencyLinksChange([{ id: 1 }]);

        expect(programmingExercise.competencyLinks).toEqual([{ id: 1 }]);
        expect(emitSpy).toHaveBeenCalled();
    });

    it('should handle handleProblemStatementAction for generate', () => {
        const programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.course = { id: 42 } as any;
        programmingExercise.problemStatement = ''; // Empty, should trigger generate
        fixture.componentRef.setInput('programmingExercise', programmingExercise);

        const generateSpy = jest.spyOn(comp, 'generateProblemStatement');

        comp.userPrompt = 'Test';
        comp.handleProblemStatementAction();

        expect(generateSpy).toHaveBeenCalled();
    });

    it('should clear all comments', () => {
        comp.clearAllComments();

        expect(mockInlineCommentService.clearAll).toHaveBeenCalled();
    });

    it('should save inline comment (new)', () => {
        mockInlineCommentService.getComment.mockReturnValue(undefined);
        const comment = { id: 'c1', startLine: 1, endLine: 2, instruction: 'Fix this', status: 'pending' as const, createdAt: new Date() };

        comp.onSaveInlineComment(comment);

        expect(mockInlineCommentService.addExistingComment).toHaveBeenCalledWith({
            id: comment.id,
            startLine: comment.startLine,
            endLine: comment.endLine,
            instruction: comment.instruction,
            status: 'pending',
            createdAt: comment.createdAt,
        });
    });

    it('should update existing inline comment', () => {
        const existingComment = { id: 'c1', startLine: 1, endLine: 2, instruction: 'Fix', status: 'applied' as const, createdAt: new Date() };
        mockInlineCommentService.getComment.mockReturnValue(existingComment);

        comp.onSaveInlineComment(existingComment);

        expect(mockInlineCommentService.updateStatus).toHaveBeenCalledWith('c1', 'pending');
    });

    it('should delete inline comment', () => {
        comp.onDeleteInlineComment('c1');

        expect(mockInlineCommentService.removeComment).toHaveBeenCalledWith('c1');
    });

    it('should cancel inline comment apply operation', () => {
        (comp as any).applyingCommentId.set('c1');
        (comp as any).isApplyingAll.set(true);

        comp.onCancelInlineCommentApply();

        expect(mockInlineCommentService.updateStatus).toHaveBeenCalledWith('c1', 'pending');
        expect((comp as any).applyingCommentId()).toBeUndefined();
        expect((comp as any).isApplyingAll()).toBeFalse();
    });
});

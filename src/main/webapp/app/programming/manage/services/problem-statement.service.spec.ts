import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { ProblemStatementService } from './problem-statement.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { FileService } from 'app/shared/service/file.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

describe('ProblemStatementService', () => {
    let service: ProblemStatementService;
    let hyperionApiService: jest.Mocked<HyperionProblemStatementApiService>;
    let fileService: jest.Mocked<FileService>;
    let alertService: jest.Mocked<AlertService>;

    const mockExerciseWithId: ProgrammingExercise = {
        id: 42,
        course: { id: 1 },
        programmingLanguage: ProgrammingLanguage.JAVA,
        projectType: ProjectType.PLAIN_GRADLE,
    } as ProgrammingExercise;

    const mockExerciseWithoutId: ProgrammingExercise = {
        course: { id: 1 },
        programmingLanguage: ProgrammingLanguage.JAVA,
        projectType: ProjectType.PLAIN_GRADLE,
    } as ProgrammingExercise;

    beforeEach(() => {
        const hyperionApiServiceMock = {
            generateProblemStatement: jest.fn(),
            refineProblemStatementGlobally: jest.fn(),
            refineProblemStatementTargeted: jest.fn(),
        };

        const fileServiceMock = {
            getTemplateFile: jest.fn(),
        };

        const alertServiceMock = {
            success: jest.fn(),
            error: jest.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementService,
                { provide: HyperionProblemStatementApiService, useValue: hyperionApiServiceMock },
                { provide: FileService, useValue: fileServiceMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        });

        service = TestBed.inject(ProblemStatementService);
        hyperionApiService = TestBed.inject(HyperionProblemStatementApiService) as jest.Mocked<HyperionProblemStatementApiService>;
        fileService = TestBed.inject(FileService) as jest.Mocked<FileService>;
        alertService = TestBed.inject(AlertService) as jest.Mocked<AlertService>;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('loadTemplate', () => {
        it('should load template successfully', () => {
            const mockTemplate = '# Problem Statement Template';
            fileService.getTemplateFile.mockReturnValue(of(mockTemplate) as any);

            const templateSignal = signal('');
            const loadedSignal = signal(false);

            service.loadTemplate(mockExerciseWithId, templateSignal, loadedSignal);

            expect(fileService.getTemplateFile).toHaveBeenCalledWith(ProgrammingLanguage.JAVA, ProjectType.PLAIN_GRADLE);
            expect(templateSignal()).toBe(mockTemplate);
            expect(loadedSignal()).toBeTrue();
        });

        it('should handle missing exercise', () => {
            const templateSignal = signal('initial');
            const loadedSignal = signal(true);

            service.loadTemplate(undefined, templateSignal, loadedSignal);

            expect(fileService.getTemplateFile).not.toHaveBeenCalled();
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });

        it('should handle exercise without programming language', () => {
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate({} as ProgrammingExercise, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });

        it('should handle template loading error', () => {
            fileService.getTemplateFile.mockReturnValue(throwError(() => new Error('fail')));
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate(mockExerciseWithId, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });
    });

    describe('generateProblemStatement', () => {
        it('should pass exerciseId when exercise has an id', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            hyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const prompt = 'Create a sorting exercise';

            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithId, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Generated statement');
            expect(hyperionApiService.generateProblemStatement).toHaveBeenCalledWith(1, { userPrompt: prompt }, 42);
        });

        it('should pass undefined exerciseId when exercise has no id', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            hyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const prompt = 'Create a sorting exercise';

            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithoutId, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Generated statement');
            expect(hyperionApiService.generateProblemStatement).toHaveBeenCalledWith(1, { userPrompt: prompt }, undefined);
        });

        it('should return failure when exercise is undefined', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(undefined, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
        }));

        it('should return failure when prompt is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(mockExerciseWithId, '   ', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
        }));

        it('should set loading signal correctly', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            const subject = new Subject<any>();
            hyperionApiService.generateProblemStatement.mockReturnValue(subject as any);

            const loadingSignal = signal(false);
            const prompt = 'Create exercise';

            expect(loadingSignal()).toBeFalse();

            const completion = new Promise<void>((resolve, reject) => {
                service.generateProblemStatement(mockExerciseWithId, prompt, loadingSignal).subscribe({
                    next: () => {},
                    error: reject,
                    complete: resolve,
                });
            });

            expect(loadingSignal()).toBeTrue();

            subject.next(mockResponse);
            expect(loadingSignal()).toBeTrue();

            subject.complete();
            await completion;

            expect(loadingSignal()).toBeFalse();
        });

        it('should handle API errors gracefully', async () => {
            hyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('API Error')) as any);

            const loadingSignal = signal(false);
            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithId, 'Create exercise', loadingSignal));

            expect(result.success).toBeFalse();
            expect(result.errorHandled).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        });

        it('should handle invalid generation response', fakeAsync(() => {
            hyperionApiService.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }) as any);
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(mockExerciseWithId, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(alertService.success).not.toHaveBeenCalled();
        }));

        it('should detect interceptor-handled HTTP errors', fakeAsync(() => {
            const httpError = new HttpErrorResponse({ error: { errorKey: 'someError' }, status: 400 });
            hyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => httpError));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(mockExerciseWithId, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(result.errorHandled).toBeTrue();
        }));
    });

    describe('refineGlobally', () => {
        it('should pass exerciseId when exercise has an id', async () => {
            const mockResponse = { refinedProblemStatement: 'Refined statement' };
            hyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const currentContent = 'Original content';
            const prompt = 'Make it better';

            const result = await firstValueFrom(service.refineGlobally(mockExerciseWithId, currentContent, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Refined statement');
            expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(1, { problemStatementText: currentContent, userPrompt: prompt }, 42);
        });

        it('should pass undefined exerciseId when exercise has no id', async () => {
            const mockResponse = { refinedProblemStatement: 'Refined statement' };
            hyperionApiService.refineProblemStatementGlobally.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const currentContent = 'Original content';
            const prompt = 'Make it better';

            const result = await firstValueFrom(service.refineGlobally(mockExerciseWithoutId, currentContent, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Refined statement');
            expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(1, { problemStatementText: currentContent, userPrompt: prompt }, undefined);
        });

        it('should handle empty content', async () => {
            const loadingSignal = signal(false);

            const result = await firstValueFrom(service.refineGlobally(mockExerciseWithId, '', 'prompt', loadingSignal));

            expect(result.success).toBeFalse();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
            expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
        });

        it('should return failure when prompt is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(mockExerciseWithId, 'content', '', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
        }));

        it('should return failure when exercise has no course', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally({} as ProgrammingExercise, 'content', 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
        }));

        it('should handle API error during global refinement', fakeAsync(() => {
            hyperionApiService.refineProblemStatementGlobally.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(mockExerciseWithId, 'content', 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        }));
    });

    describe('refineTargeted', () => {
        const mockEvent = {
            instruction: 'Add examples',
            startLine: 1,
            endLine: 3,
            startColumn: 0,
            endColumn: 10,
        };

        it('should pass exerciseId when exercise has an id', async () => {
            const mockResponse = { refinedProblemStatement: 'Refined statement' };
            hyperionApiService.refineProblemStatementTargeted.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const currentContent = 'Original content';

            const result = await firstValueFrom(service.refineTargeted(mockExerciseWithId, currentContent, mockEvent, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Refined statement');
            expect(hyperionApiService.refineProblemStatementTargeted).toHaveBeenCalledWith(
                1,
                expect.objectContaining({
                    problemStatementText: currentContent,
                    instruction: mockEvent.instruction,
                    startLine: mockEvent.startLine,
                    endLine: mockEvent.endLine,
                }),
                42,
            );
        });

        it('should pass undefined exerciseId when exercise has no id', async () => {
            const mockResponse = { refinedProblemStatement: 'Refined statement' };
            hyperionApiService.refineProblemStatementTargeted.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const currentContent = 'Original content';

            const result = await firstValueFrom(service.refineTargeted(mockExerciseWithoutId, currentContent, mockEvent, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Refined statement');
            expect(hyperionApiService.refineProblemStatementTargeted).toHaveBeenCalledWith(
                1,
                expect.objectContaining({
                    problemStatementText: currentContent,
                    instruction: mockEvent.instruction,
                }),
                undefined,
            );
        });

        it('should handle empty content', async () => {
            const loadingSignal = signal(false);

            const result = await firstValueFrom(service.refineTargeted(mockExerciseWithId, '', mockEvent, loadingSignal));

            expect(result.success).toBeFalse();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
            expect(hyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
        });

        it('should return failure when exercise has no course', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted({} as ProgrammingExercise, 'content', mockEvent, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
        }));

        it('should handle API error during targeted refinement', fakeAsync(() => {
            hyperionApiService.refineProblemStatementTargeted.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(mockExerciseWithId, 'content', mockEvent, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        }));
    });
});

import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { Subject, firstValueFrom, of, throwError } from 'rxjs';
import { ProblemStatementService } from './problem-statement.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { FileService } from 'app/shared/service/file.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

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

    describe('generateProblemStatement', () => {
        it('should pass exerciseId when exercise has an id', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            hyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const prompt = 'Create a sorting exercise';

            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithId, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Generated statement');
            expect(hyperionApiService.generateProblemStatement).toHaveBeenCalledWith(
                1, // courseId
                { userPrompt: prompt },
                42, // exerciseId
            );
        });

        it('should pass undefined exerciseId when exercise has no id', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            hyperionApiService.generateProblemStatement.mockReturnValue(of(mockResponse) as any);

            const loadingSignal = signal(false);
            const prompt = 'Create a sorting exercise';

            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithoutId, prompt, loadingSignal));

            expect(result.success).toBeTrue();
            expect(result.content).toBe('Generated statement');
            expect(hyperionApiService.generateProblemStatement).toHaveBeenCalledWith(
                1, // courseId
                { userPrompt: prompt },
                undefined, // exerciseId is undefined during creation
            );
        });

        it('should set loading signal correctly', async () => {
            const mockResponse = { draftProblemStatement: 'Generated statement' };
            const subject = new Subject<any>();
            hyperionApiService.generateProblemStatement.mockReturnValue(subject as any);

            const loadingSignal = signal(false);
            const prompt = 'Create exercise';

            expect(loadingSignal()).toBeFalse();

            // Subscribe and await completion without using `done`
            const completion = new Promise<void>((resolve, reject) => {
                service.generateProblemStatement(mockExerciseWithId, prompt, loadingSignal).subscribe({
                    next: () => {
                        // no-op; we check signals separately
                    },
                    error: reject,
                    complete: resolve,
                });
            });

            // immediately after subscription, it should be loading
            expect(loadingSignal()).toBeTrue();

            // emit + complete
            subject.next(mockResponse);
            expect(loadingSignal()).toBeTrue();

            subject.complete();

            // wait for observable to finalize/complete
            await completion;

            expect(loadingSignal()).toBeFalse();
        });

        it('should handle API errors gracefully', async () => {
            hyperionApiService.generateProblemStatement.mockReturnValue(throwError(() => new Error('API Error')) as any);

            const loadingSignal = signal(false);
            const prompt = 'Create exercise';

            const result = await firstValueFrom(service.generateProblemStatement(mockExerciseWithId, prompt, loadingSignal));

            expect(result.success).toBeFalse();
            expect(alertService.error).toHaveBeenCalled();
            expect(loadingSignal()).toBeFalse();
        });
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
            expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(
                1, // courseId
                { problemStatementText: currentContent, userPrompt: prompt },
                42, // exerciseId
            );
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
            expect(hyperionApiService.refineProblemStatementGlobally).toHaveBeenCalledWith(
                1, // courseId
                { problemStatementText: currentContent, userPrompt: prompt },
                undefined, // exerciseId is undefined during creation
            );
        });

        it('should handle empty content', async () => {
            const loadingSignal = signal(false);

            const result = await firstValueFrom(service.refineGlobally(mockExerciseWithId, '', 'prompt', loadingSignal));

            expect(result.success).toBeFalse();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
            expect(hyperionApiService.refineProblemStatementGlobally).not.toHaveBeenCalled();
        });
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
                1, // courseId
                expect.objectContaining({
                    problemStatementText: currentContent,
                    instruction: mockEvent.instruction,
                    startLine: mockEvent.startLine,
                    endLine: mockEvent.endLine,
                }),
                42, // exerciseId
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
                1, // courseId
                expect.objectContaining({
                    problemStatementText: currentContent,
                    instruction: mockEvent.instruction,
                }),
                undefined, // exerciseId is undefined during creation
            );
        });

        it('should handle empty content', async () => {
            const loadingSignal = signal(false);

            const result = await firstValueFrom(service.refineTargeted(mockExerciseWithId, '', mockEvent, loadingSignal));

            expect(result.success).toBeFalse();
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.programmingExercise.inlineRefine.error');
            expect(hyperionApiService.refineProblemStatementTargeted).not.toHaveBeenCalled();
        });
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
    });
});

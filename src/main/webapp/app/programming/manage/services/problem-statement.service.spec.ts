import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ProblemStatementService } from './problem-statement.service';
import { FileService } from 'app/shared/service/file.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('ProblemStatementService', () => {
    let service: ProblemStatementService;
    let fileServiceMock: { getTemplateFile: ReturnType<typeof vi.fn> };
    let hyperionApiMock: {
        generateProblemStatement: ReturnType<typeof vi.fn>;
        refineProblemStatementGlobally: ReturnType<typeof vi.fn>;
        refineProblemStatementTargeted: ReturnType<typeof vi.fn>;
    };
    let alertServiceMock: { success: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

    const exerciseWithCourse = {
        course: { id: 42 } as Course,
        programmingLanguage: ProgrammingLanguage.JAVA,
    } as ProgrammingExercise;

    beforeEach(() => {
        fileServiceMock = {
            getTemplateFile: vi.fn(),
        } as any;
        hyperionApiMock = {
            generateProblemStatement: vi.fn(),
            refineProblemStatementGlobally: vi.fn(),
            refineProblemStatementTargeted: vi.fn(),
        } as any;
        alertServiceMock = {
            success: vi.fn(),
            error: vi.fn(),
        } as any;

        TestBed.configureTestingModule({
            providers: [
                ProblemStatementService,
                { provide: FileService, useValue: fileServiceMock },
                { provide: HyperionProblemStatementApiService, useValue: hyperionApiMock },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        });
        service = TestBed.inject(ProblemStatementService);
    });

    describe('loadTemplate', () => {
        it('should return empty template when exercise is undefined', () => {
            let result: any;
            service.loadTemplate(undefined).subscribe((r) => (result = r));
            expect(result.template).toBe('');
            expect(result.loaded).toBeFalsy();
        });

        it('should return empty template when exercise has no programming language', () => {
            let result: any;
            service.loadTemplate({} as ProgrammingExercise).subscribe((r) => (result = r));
            expect(result.template).toBe('');
            expect(result.loaded).toBeFalsy();
        });

        it('should load template successfully', () => {
            fileServiceMock.getTemplateFile.mockReturnValue(of('template content'));
            let result: any;
            service.loadTemplate(exerciseWithCourse).subscribe((r) => (result = r));
            expect(result.template).toBe('template content');
            expect(result.loaded).toBeTruthy();
        });

        it('should handle template loading error', () => {
            fileServiceMock.getTemplateFile.mockReturnValue(throwError(() => new Error('fail')));
            let result: any;
            service.loadTemplate(exerciseWithCourse).subscribe((r) => (result = r));
            expect(result.template).toBe('');
            expect(result.loaded).toBeFalsy();
        });
    });

    describe('generateProblemStatement', () => {
        it('should return failure when exercise is undefined', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(undefined, 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
        }));

        it('should return failure when prompt is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, '   ', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
        }));

        it('should generate successfully', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: 'Generated!' }) as any);
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'Generate a sorting exercise', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeTruthy();
            expect(result.content).toBe('Generated!');
            expect(loadingSignal()).toBeFalsy();
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should set loading signal during generation', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: 'Generated!' }) as any);
            const loadingSignal = signal(false);
            service.generateProblemStatement(exerciseWithCourse, 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe();
            expect(loadingSignal()).toBeFalsy();
        }));

        it('should handle invalid generation response', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }) as any);
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeFalsy();
            expect(alertServiceMock.success).not.toHaveBeenCalled();
        }));

        it('should handle API error', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(throwError(() => new Error('Network error')));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeFalsy();
            expect(result.errorHandled).toBeFalsy();
            expect(loadingSignal()).toBeFalsy();
        }));

        it('should detect interceptor-handled HTTP errors', fakeAsync(() => {
            const httpError = new HttpErrorResponse({ error: { errorKey: 'someError' }, status: 400 });
            hyperionApiMock.generateProblemStatement.mockReturnValue(throwError(() => httpError));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeFalsy();
            expect(result.errorHandled).toBeTruthy();
        }));
    });

    describe('refineGlobally', () => {
        it('should return failure and show error when content is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, '', 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.cannotRefineEmpty');
        }));

        it('should return failure when prompt is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'content', '', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
        }));

        it('should return failure when exercise has no course', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally({} as ProgrammingExercise, 'content', 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
        }));

        it('should refine globally successfully', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementGlobally.mockReturnValue(of({ refinedProblemStatement: 'Refined!' }) as any);
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'original', 'improve clarity', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeTruthy();
            expect(result.content).toBe('Refined!');
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should handle API error during global refinement', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementGlobally.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'content', 'prompt', (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeFalsy();
            expect(loadingSignal()).toBeFalsy();
        }));
    });

    describe('refineTargeted', () => {
        const event = { instruction: 'Fix this', startLine: 1, endLine: 3, startColumn: 1, endColumn: 10 };

        it('should return failure when exercise has no course', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted({} as ProgrammingExercise, 'content', event, (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
        }));

        it('should return failure when content is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, '', event, (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result).toEqual({ success: false, errorHandled: true });
            expect(alertServiceMock.error).toHaveBeenCalled();
        }));

        it('should refine targeted successfully', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementTargeted.mockReturnValue(of({ refinedProblemStatement: 'Targeted refined!' }) as any);
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, 'original content', event, (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeTruthy();
            expect(result.content).toBe('Targeted refined!');
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should handle API error during targeted refinement', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementTargeted.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, 'content', event, (v: boolean) => loadingSignal.set(v)).subscribe((r) => (result = r));
            expect(result.success).toBeFalsy();
            expect(loadingSignal()).toBeFalsy();
        }));
    });
});

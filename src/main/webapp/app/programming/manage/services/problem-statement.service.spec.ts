import { HttpErrorResponse } from '@angular/common/http';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { ProblemStatementService } from './problem-statement.service';
import { FileService } from 'app/shared/service/file.service';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Course } from 'app/core/course/course.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

describe('ProblemStatementService', () => {
    let service: ProblemStatementService;
    let fileServiceMock: jest.Mocked<FileService>;
    let hyperionApiMock: jest.Mocked<HyperionProblemStatementApiService>;
    let alertServiceMock: jest.Mocked<AlertService>;

    const exerciseWithCourse = {
        course: { id: 42 } as Course,
        programmingLanguage: ProgrammingLanguage.JAVA,
    } as ProgrammingExercise;

    beforeEach(() => {
        fileServiceMock = {
            getTemplateFile: jest.fn(),
        } as any;
        hyperionApiMock = {
            generateProblemStatement: jest.fn(),
            refineProblemStatementGlobally: jest.fn(),
            refineProblemStatementTargeted: jest.fn(),
        } as any;
        alertServiceMock = {
            success: jest.fn(),
            error: jest.fn(),
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
        it('should set empty template when exercise is undefined', () => {
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate(undefined, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });

        it('should set empty template when exercise has no programming language', () => {
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate({} as ProgrammingExercise, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });

        it('should load template successfully', () => {
            fileServiceMock.getTemplateFile.mockReturnValue(of('template content'));
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate(exerciseWithCourse, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('template content');
            expect(loadedSignal()).toBeTrue();
        });

        it('should handle template loading error', () => {
            fileServiceMock.getTemplateFile.mockReturnValue(throwError(() => new Error('fail')));
            const templateSignal = signal('');
            const loadedSignal = signal(false);
            service.loadTemplate(exerciseWithCourse, templateSignal, loadedSignal);
            expect(templateSignal()).toBe('');
            expect(loadedSignal()).toBeFalse();
        });
    });

    describe('generateProblemStatement', () => {
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
            service.generateProblemStatement(exerciseWithCourse, '   ', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
        }));

        it('should generate successfully', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: 'Generated!' }));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'Generate a sorting exercise', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeTrue();
            expect(result.content).toBe('Generated!');
            expect(loadingSignal()).toBeFalse();
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should set loading signal during generation', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: 'Generated!' }));
            const loadingSignal = signal(false);
            service.generateProblemStatement(exerciseWithCourse, 'prompt', loadingSignal).subscribe();
            tick();
            expect(loadingSignal()).toBeFalse();
        }));

        it('should handle invalid generation response', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(of({ draftProblemStatement: '' }));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(alertServiceMock.success).not.toHaveBeenCalled();
        }));

        it('should handle API error', fakeAsync(() => {
            hyperionApiMock.generateProblemStatement.mockReturnValue(throwError(() => new Error('Network error')));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(result.errorHandled).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        }));

        it('should detect interceptor-handled HTTP errors', fakeAsync(() => {
            const httpError = new HttpErrorResponse({ error: { errorKey: 'someError' }, status: 400 });
            hyperionApiMock.generateProblemStatement.mockReturnValue(throwError(() => httpError));
            const loadingSignal = signal(false);
            let result: any;
            service.generateProblemStatement(exerciseWithCourse, 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(result.errorHandled).toBeTrue();
        }));
    });

    describe('refineGlobally', () => {
        it('should return failure and show error when content is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, '', 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.refinementError');
        }));

        it('should return failure when prompt is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'content', '', loadingSignal).subscribe((r) => (result = r));
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

        it('should refine globally successfully', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementGlobally.mockReturnValue(of({ refinedProblemStatement: 'Refined!' }));
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'original', 'improve clarity', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeTrue();
            expect(result.content).toBe('Refined!');
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should handle API error during global refinement', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementGlobally.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineGlobally(exerciseWithCourse, 'content', 'prompt', loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        }));
    });

    describe('refineTargeted', () => {
        const event = { instruction: 'Fix this', startLine: 1, endLine: 3, startColumn: 0, endColumn: 10 };

        it('should return failure when exercise has no course', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted({} as ProgrammingExercise, 'content', event, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.programmingExercise.problemStatement.inlineRefinement.error');
        }));

        it('should return failure when content is empty', fakeAsync(() => {
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, '', event, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result).toEqual({ success: false });
            expect(alertServiceMock.error).toHaveBeenCalled();
        }));

        it('should refine targeted successfully', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementTargeted.mockReturnValue(of({ refinedProblemStatement: 'Targeted refined!' }));
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, 'original content', event, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeTrue();
            expect(result.content).toBe('Targeted refined!');
            expect(alertServiceMock.success).toHaveBeenCalled();
        }));

        it('should handle API error during targeted refinement', fakeAsync(() => {
            hyperionApiMock.refineProblemStatementTargeted.mockReturnValue(throwError(() => new Error('fail')));
            const loadingSignal = signal(false);
            let result: any;
            service.refineTargeted(exerciseWithCourse, 'content', event, loadingSignal).subscribe((r) => (result = r));
            tick();
            expect(result.success).toBeFalse();
            expect(loadingSignal()).toBeFalse();
        }));
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExerciseImportButtonComponent } from './exercise-import-button.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseImportComponent } from 'app/exercise/import/exercise-import.component';
import { ExerciseImportTabsComponent } from 'app/exercise/import/exercise-import-tabs/exercise-import-tabs.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Router, provideRouter } from '@angular/router';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClient } from '@angular/common/http';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { Subject } from 'rxjs';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgrammingExerciseResolve } from 'app/programming/manage/services/programming-exercise-resolve.service';
import { TextExerciseResolver } from 'app/text/manage/text-exercise/service/text-exercise-resolver.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

describe('ExerciseImportButtonComponent', () => {
    let component: ExerciseImportButtonComponent;
    let fixture: ComponentFixture<ExerciseImportButtonComponent>;
    let dialogService: DialogService;
    let router: Router;
    let httpMock: HttpTestingController;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent, ExerciseImportButtonComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                provideRouter([
                    {
                        path: 'course-management/:courseId/programming-exercises/import/:exerciseId',
                        children: [],
                        resolve: { exercise: ProgrammingExerciseResolve },
                    },
                    {
                        path: 'course-management/:courseId/text-exercises/:exerciseId/import',
                        children: [],
                        resolve: { exercise: TextExerciseResolver },
                    },
                    { path: 'course-management/:courseId/programming-exercises/import-from-file', children: [] },
                ]),
                LocalStorageService,
                SessionStorageService,
                { provide: DialogService, useClass: MockDialogService },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportButtonComponent);
        component = fixture.componentInstance;
        dialogService = TestBed.inject(DialogService);
        fixture.componentRef.setInput('course', { id: 123 });
        fixture.componentRef.setInput('exerciseType', ExerciseType.MODELING);
        router = TestBed.inject(Router);
        httpMock = TestBed.inject(HttpTestingController);
        fixture.detectChanges();
    });

    afterEach(() => httpMock.verify());

    it.each([
        { exerciseType: ExerciseType.MODELING, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.TEXT, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.FILE_UPLOAD, id: 2, expectedComponent: ExerciseImportComponent },
        { exerciseType: ExerciseType.PROGRAMMING, id: 2, expectedComponent: ExerciseImportTabsComponent },
        { exerciseType: ExerciseType.PROGRAMMING, id: undefined, expectedComponent: ExerciseImportTabsComponent },
    ])('should open import modal', async ({ exerciseType, id, expectedComponent }) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);

        const onCloseSubject = new Subject<Exercise | undefined>();
        const mockDialogRef = { onClose: onCloseSubject.asObservable() } as DynamicDialogRef;
        const openSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const routerSpy = vi.spyOn(router, 'navigate').mockReturnValue(Promise.resolve(true));
        const beforeNavigateSpy = vi.spyOn(component.beforeNavigate, 'emit');

        component.openImportModal();

        expect(beforeNavigateSpy).toHaveBeenCalledTimes(1);
        expect(openSpy).toHaveBeenCalledTimes(1);
        expect(openSpy).toHaveBeenCalledWith(
            expectedComponent,
            expect.objectContaining({
                data: { exerciseType },
            }),
        );

        // Simulate dialog closing with result
        onCloseSubject.next({ id, maxPoints: 1 } as Exercise);
        onCloseSubject.complete();

        // Wait for async operations
        await fixture.whenStable();

        if (id && exerciseType === ExerciseType.PROGRAMMING) {
            expect(routerSpy).toHaveBeenCalledTimes(1);
            expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 'import', 2], {
                queryParams: { sourceIsExamExercise: false },
            });
        } else if (!id && exerciseType === ExerciseType.PROGRAMMING) {
            expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, 'programming-exercises', 'import-from-file'], {
                state: { programmingExerciseForImportFromFile: { id: undefined, maxPoints: 1 } },
            });
        } else {
            expect(routerSpy).toHaveBeenCalledTimes(1);
            expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, `${exerciseType}-exercises`, 2, 'import'], {
                queryParams: { sourceIsExamExercise: false },
            });
        }
    });

    it.each([
        {
            exerciseType: ExerciseType.PROGRAMMING,
            sourceIsExam: true,
            expectedUrl: 'api/programming/programming-exercises/42/for-exam',
            expectedPath: '/course-management/123/programming-exercises/import/42',
        },
        {
            exerciseType: ExerciseType.PROGRAMMING,
            sourceIsExam: false,
            expectedUrl: 'api/programming/programming-exercises/42',
            expectedPath: '/course-management/123/programming-exercises/import/42',
        },
        {
            exerciseType: ExerciseType.TEXT,
            sourceIsExam: true,
            expectedUrl: 'api/text/text-exercises/42/for-exam',
            expectedPath: '/course-management/123/text-exercises/42/import',
        },
        {
            exerciseType: ExerciseType.TEXT,
            sourceIsExam: false,
            expectedUrl: 'api/text/text-exercises/42',
            expectedPath: '/course-management/123/text-exercises/42/import',
        },
    ])('loads the selected $exerciseType source (exam: $sourceIsExam) when importing into a course', async ({ exerciseType, sourceIsExam, expectedUrl, expectedPath }) => {
        fixture.componentRef.setInput('exerciseType', exerciseType);
        // Both paged search DTOs retain this group/exam/course reference for exam sources.
        // Text search also flattens the source course; it does not identify the destination.
        const sourceCourse = { id: 456, title: 'Source course' };
        const group = sourceIsExam ? { id: 7, exam: { id: 8, title: 'Source exam', course: sourceCourse } } : undefined;
        const source = exerciseType === ExerciseType.PROGRAMMING ? new ProgrammingExercise(sourceIsExam ? undefined : sourceCourse, group) : new TextExercise(sourceCourse, group);
        source.id = 42;
        source.title = 'Selected source';
        const dialogRef = new DynamicDialogRef();
        vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.openImportModal();
        dialogRef.close(source);

        // Run the actual navigation, route-context helper, resolver and detail service.
        const request = await vi.waitFor(() => httpMock.expectOne({ method: 'GET' }));
        request.flush(source);
        expect(await navigateSpy.mock.results[0].value).toBe(true);
        expect(request.request.url).toBe(expectedUrl);
        expect(request.request.params.get('withPlagiarismDetectionConfig')).toBe('true');
        expect(router.url.split('?')[0]).toBe(expectedPath);
        expect(router.routerState.snapshot.root.firstChild?.data['exercise'].id).toBe(42);
    });

    it('keeps a programming file import in navigation state without loading source details', async () => {
        fixture.componentRef.setInput('exerciseType', ExerciseType.PROGRAMMING);
        const source = new ProgrammingExercise(undefined, undefined);
        source.title = 'Imported archive';
        const dialogRef = new DynamicDialogRef();
        vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.openImportModal();
        dialogRef.close(source);

        expect(await navigateSpy.mock.results[0].value).toBe(true);
        expect(router.url).toBe('/course-management/123/programming-exercises/import-from-file');
        expect(router.lastSuccessfulNavigation()?.extras.state?.['programmingExerciseForImportFromFile']).toBe(source);
        expect(router.lastSuccessfulNavigation()?.extras.queryParams).toBeUndefined();
        httpMock.expectNone(() => true);
    });

    it('does not navigate when the import dialog is dismissed', () => {
        const dialogRef = new DynamicDialogRef();
        vi.spyOn(dialogService, 'open').mockReturnValue(dialogRef);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.openImportModal();
        dialogRef.close();

        expect(navigateSpy).not.toHaveBeenCalled();
    });
});

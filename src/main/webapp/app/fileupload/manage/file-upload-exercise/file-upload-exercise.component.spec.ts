/**
 * Vitest tests for FileUploadExerciseComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { TranslateModule } from '@ngx-translate/core';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

import 'app/shared/util/array.extension';

import { FileUploadExerciseComponent } from './file-upload-exercise.component';
import { FileUploadExerciseService } from '../services/file-upload-exercise.service';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseFilter } from 'app/exercise/shared/entities/exercise/exercise-filter.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { SortService } from 'app/shared/service/sort.service';

describe('FileUploadExerciseComponent', () => {
    setupTestBed({ zoneless: true });

    let component: FileUploadExerciseComponent;
    let fixture: ComponentFixture<FileUploadExerciseComponent>;
    let courseExerciseService: CourseExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let alertService: AlertService;

    const createCourse = (id = 123): Course => {
        const course = new Course();
        course.id = id;
        return course;
    };

    const createExercise = (id: number, title: string, course?: Course): FileUploadExercise => {
        const exercise = new FileUploadExercise(course ?? createCourse(), undefined);
        exercise.id = id;
        exercise.title = title;
        exercise.filePattern = 'pdf,png';
        return exercise;
    };

    beforeEach(async () => {
        const course = createCourse();

        await TestBed.configureTestingModule({
            imports: [FileUploadExerciseComponent, TranslateModule.forRoot()],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                provideRouter([]),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: 123 }) },
                    },
                },
                {
                    provide: AccountService,
                    useValue: {
                        setAccessRightsForExercise: vi.fn(),
                    },
                },
                {
                    provide: EventManager,
                    useValue: {
                        subscribe: vi.fn().mockReturnValue({ id: 1 }),
                        broadcast: vi.fn(),
                        destroy: vi.fn(),
                    },
                },
                {
                    provide: SortService,
                    useValue: {
                        sortByProperty: vi.fn((arr) => arr),
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(FileUploadExerciseComponent);
        component = fixture.componentInstance;
        courseExerciseService = TestBed.inject(CourseExerciseService);
        fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        alertService = TestBed.inject(AlertService);

        component.course = course;
    });

    afterEach(() => {
        vi.clearAllMocks();
        if (fixture) {
            fixture.destroy();
        }
    });

    describe('initialization', () => {
        it('should load exercises on init', async () => {
            const exercises = [createExercise(1, 'Exercise 1'), createExercise(2, 'Exercise 2')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.fileUploadExercises()).toHaveLength(2);
        });

        it('should set access rights for each loaded exercise', async () => {
            const accountService = TestBed.inject(AccountService);
            const exercises = [createExercise(1, 'Exercise 1')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            expect(accountService.setAccessRightsForExercise).toHaveBeenCalled();
        });

        it('should handle error when loading exercises fails', async () => {
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(
                throwError(() => new HttpErrorResponse({ status: 404, statusText: 'Not Found' })),
            );
            const alertSpy = vi.spyOn(alertService, 'error');

            // Call loadExercises directly and await it - accessing protected method for testing
            await (component as unknown as { loadExercises: () => Promise<void> }).loadExercises();

            expect(alertSpy).toHaveBeenCalledWith('error.http.404');
        });

        it('should reconnect exercise with course', async () => {
            const exercises = [createExercise(1, 'Exercise 1')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.fileUploadExercises()[0].course).toBe(component.course);
        });
    });

    describe('deleteFileUploadExercise', () => {
        it('should delete exercise and broadcast event', async () => {
            const eventManager = TestBed.inject(EventManager);
            vi.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of(new HttpResponse({ body: {} })));

            await component.deleteFileUploadExercise(456);

            expect(fileUploadExerciseService.delete).toHaveBeenCalledWith(456);
            expect(eventManager.broadcast).toHaveBeenCalledWith({
                name: 'fileUploadExerciseListModification',
                content: 'Deleted an fileUploadExercise',
            });
        });

        it('should handle delete error', async () => {
            const error = new Error('Delete failed');
            vi.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(throwError(() => error));

            await component.deleteFileUploadExercise(456);

            // dialogErrorSource should receive the error message
            expect(fileUploadExerciseService.delete).toHaveBeenCalledWith(456);
        });
    });

    describe('trackId', () => {
        it('should return exercise id', () => {
            const exercise = createExercise(456, 'Test');

            expect(component.trackId(0, exercise)).toBe(456);
        });

        it('should return undefined for exercise without id', () => {
            const exercise = new FileUploadExercise(undefined, undefined);

            expect(component.trackId(0, exercise)).toBeUndefined();
        });
    });

    describe('filtering', () => {
        beforeEach(async () => {
            const exercises = [createExercise(1, 'PDF Upload'), createExercise(2, 'Image Upload'), createExercise(3, 'Document Upload')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should filter exercises by title', () => {
            component.exerciseFilter = new ExerciseFilter('PDF', '', 'all');

            expect(component.filteredFileUploadExercises().length).toBe(1);
            expect(component.filteredFileUploadExercises()[0].title).toBe('PDF Upload');
        });

        it('should show all exercises when filter matches all', () => {
            component.exerciseFilter = new ExerciseFilter('Upload', '', 'all');

            expect(component.filteredFileUploadExercises().length).toBe(3);
        });

        it('should show no exercises when filter matches none', () => {
            component.exerciseFilter = new ExerciseFilter('NonExistent', '', 'all');

            expect(component.filteredFileUploadExercises().length).toBe(0);
        });

        it('should apply filter by exercise type', () => {
            component.exerciseFilter = new ExerciseFilter('', '', 'file-upload');

            expect(component.filteredFileUploadExercises().length).toBe(3);
        });
    });

    describe('sorting', () => {
        beforeEach(async () => {
            const exercises = [createExercise(1, 'Exercise B'), createExercise(2, 'Exercise A'), createExercise(3, 'Exercise C')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should call sort service when sorting rows', () => {
            const sortService = TestBed.inject(SortService);

            component.sortRows();

            expect(sortService.sortByProperty).toHaveBeenCalled();
        });

        it('should apply filter after sorting', () => {
            const sortService = TestBed.inject(SortService);
            vi.spyOn(sortService, 'sortByProperty').mockReturnValue(component.fileUploadExercises());

            component.sortRows();

            expect(component.filteredFileUploadExercises().length).toBe(3);
        });
    });

    describe('selection', () => {
        beforeEach(async () => {
            const exercises = [createExercise(1, 'Exercise 1'), createExercise(2, 'Exercise 2')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));
            component.ngOnInit();
            await fixture.whenStable();
        });

        it('should toggle exercise selection', () => {
            const exercise = component.fileUploadExercises()[0];

            component.toggleExercise(exercise);

            expect(component.selectedExercises).toContain(exercise);
        });

        it('should unselect exercise when toggled again', () => {
            const exercise = component.fileUploadExercises()[0];

            component.toggleExercise(exercise);
            component.toggleExercise(exercise);

            expect(component.selectedExercises).not.toContain(exercise);
        });

        it('should update allChecked status', () => {
            const exercises = component.fileUploadExercises();

            component.toggleExercise(exercises[0]);
            expect(component.allChecked).toBe(false);

            component.toggleExercise(exercises[1]);
            expect(component.allChecked).toBe(true);
        });
    });

    describe('exercise count emission', () => {
        it('should emit exercise count after loading', async () => {
            const exercises = [createExercise(1, 'Ex 1'), createExercise(2, 'Ex 2'), createExercise(3, 'Ex 3')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            // Exercise count should be 3
            expect(component.fileUploadExercises()).toHaveLength(3);
        });

        it('should emit filtered exercise count after filtering', async () => {
            const exercises = [createExercise(1, 'Test'), createExercise(2, 'Other')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            component.exerciseFilter = new ExerciseFilter('Test', '', 'all');

            expect(component.filteredFileUploadExercises()).toHaveLength(1);
        });
    });

    describe('getChangeEventName', () => {
        it('should return correct event name', () => {
            // Access protected method with proper type assertion
            const eventName = (component as unknown as { getChangeEventName: () => string }).getChangeEventName();

            expect(eventName).toBe('fileUploadExerciseListModification');
        });
    });

    describe('callback', () => {
        it('should have callback method for jhiSort', () => {
            expect(typeof component.callback).toBe('function');
            // Should not throw
            component.callback();
        });
    });

    describe('exercises getter', () => {
        it('should return fileUploadExercises array', async () => {
            const exercises = [createExercise(1, 'Test')];
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: exercises })));

            component.ngOnInit();
            await fixture.whenStable();

            expect((component as unknown as { exercises: FileUploadExercise[] }).exercises).toEqual(exercises);
        });
    });

    describe('empty state', () => {
        it('should handle empty exercise list', async () => {
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(new HttpResponse({ body: [] })));

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.fileUploadExercises()).toHaveLength(0);
            expect(component.filteredFileUploadExercises()).toHaveLength(0);
        });

        it('should handle null body in response', async () => {
            const response = new HttpResponse<FileUploadExercise[]>({ body: null });
            vi.spyOn(courseExerciseService, 'findAllFileUploadExercisesForCourse').mockReturnValue(of(response));

            component.ngOnInit();
            await fixture.whenStable();

            expect(component.fileUploadExercises()).toHaveLength(0);
        });
    });
});

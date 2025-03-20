import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { PdfPreviewDateBoxComponent } from 'app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { AlertService } from 'app/shared/service/alert.service';

describe('PdfPreviewDateBoxComponent', () => {
    let component: PdfPreviewDateBoxComponent;
    let fixture: ComponentFixture<PdfPreviewDateBoxComponent>;
    let courseExerciseServiceMock: any;
    let alertServiceMock: any;

    const mockCourse = { id: 1, title: 'Test Course' };
    const mockExercises = [
        { id: 1, type: ExerciseType.QUIZ, dueDate: dayjs('2024-02-01T10:00') },
        { id: 2, type: ExerciseType.QUIZ, dueDate: dayjs('2024-02-02T10:00') },
        { id: 3, type: ExerciseType.PROGRAMMING, dueDate: dayjs('2024-02-03T10:00') },
        { id: 4, type: ExerciseType.PROGRAMMING, dueDate: null }, // Should be filtered out
    ] as Exercise[];

    beforeEach(async () => {
        courseExerciseServiceMock = {
            findAllExercisesForCourse: jest.fn().mockReturnValue(of(new HttpResponse({ body: mockExercises }))),
        };

        alertServiceMock = {
            error: jest.fn(),
        };

        await TestBed.configureTestingModule({
            imports: [PdfPreviewDateBoxComponent],
            providers: [
                { provide: CourseExerciseService, useValue: courseExerciseServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: alertServiceMock },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewDateBoxComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('course', mockCourse);
        fixture.componentRef.setInput('pageIndices', [1, 2]);

        fixture.detectChanges();
    });

    describe('Initialization', () => {
        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should load exercises on init', fakeAsync(() => {
            component.ngOnInit();
            tick();

            expect(courseExerciseServiceMock.findAllExercisesForCourse).toHaveBeenCalledWith(mockCourse.id);
            expect(component.exercises()).toEqual(mockExercises);
        }));

        it('should handle error when loading exercises', fakeAsync(() => {
            courseExerciseServiceMock.findAllExercisesForCourse.mockReturnValue(throwError(() => new Error('Failed')));

            component.ngOnInit();
            tick();

            expect(component.exercises()).toEqual([]);
        }));

        it('should set isMultiplePages correctly', () => {
            component.ngOnInit();
            expect(component.isMultiplePages()).toBeTruthy();

            fixture.componentRef.setInput('pageIndices', [1]);
            component.ngOnInit();
            expect(component.isMultiplePages()).toBeFalsy();
        });
    });

    describe('Exercise Processing', () => {
        it('should correctly categorize and sort exercises', fakeAsync(() => {
            const futureDate = new Date();
            futureDate.setFullYear(futureDate.getFullYear() + 1);

            const futureMockExercises = [
                { id: 1, type: ExerciseType.QUIZ, dueDate: dayjs(futureDate).add(1, 'day') },
                { id: 2, type: ExerciseType.QUIZ, dueDate: dayjs(futureDate).add(2, 'days') },
                { id: 3, type: ExerciseType.PROGRAMMING, dueDate: dayjs(futureDate).add(3, 'days') },
                { id: 4, type: ExerciseType.PROGRAMMING, dueDate: null },
            ] as Exercise[];

            courseExerciseServiceMock.findAllExercisesForCourse.mockReturnValue(of(new HttpResponse({ body: futureMockExercises })));

            component.ngOnInit();
            tick();

            const categorized = component.categorizedExercises();
            expect(categorized).toHaveLength(2);
            expect(categorized[0].type).toBe(ExerciseType.QUIZ);
            expect(categorized[0].exercises).toHaveLength(2);
            expect(categorized[0].exercises[0].id).toBe(1); // Earlier date first
            expect(categorized[1].type).toBe(ExerciseType.PROGRAMMING);
            expect(categorized[1].exercises).toHaveLength(1); // Only one with dueDate
        }));
    });

    describe('Date Formatting', () => {
        it('should format date correctly', () => {
            const testDate = new Date(2024, 0, 15, 14, 30); // Jan 15, 2024, 14:30
            const formatted = component.formatDate(testDate);
            expect(formatted).toBe('2024-01-15T14:30');
        });

        it('should format due date correctly', () => {
            const testDate = dayjs('2024-01-15T14:30');
            const formatted = component.formatDueDate(testDate);
            expect(formatted).toBe('Jan 15, 2024 - 14:30');
        });
    });

    describe('Selection Handling', () => {
        it('should handle calendar selection', () => {
            component.selectCalendar();

            expect(component.calendarSelected()).toBeTruthy();
            expect(component.exerciseSelected()).toBeFalsy();
        });

        it('should handle exercise selection', () => {
            component.selectExercise();

            expect(component.calendarSelected()).toBeFalsy();
            expect(component.exerciseSelected()).toBeTruthy();
        });

        it('should handle hide forever changes', () => {
            component.calendarSelected.set(true);
            component.exerciseSelected.set(true);
            component.selectedExercise.set(mockExercises[0]);

            component.onHideForeverChange(true);

            expect(component.hideForever()).toBeTruthy();
            expect(component.calendarSelected()).toBeFalsy();
            expect(component.exerciseSelected()).toBeFalsy();
            expect(component.selectedExercise()).toBeNull();
        });
    });

    describe('Form Submission', () => {
        let hiddenPagesOutputSpy: jest.SpyInstance;

        beforeEach(() => {
            hiddenPagesOutputSpy = jest.spyOn(component.hiddenPagesOutput, 'emit');
        });

        it('should emit hidden pages with forever date when hide forever is selected', () => {
            component.hideForever.set(true);
            fixture.componentRef.setInput('pageIndices', [1, 2]);
            component.onSubmit();

            expect(hiddenPagesOutputSpy).toHaveBeenCalledWith([
                {
                    pageIndex: 1,
                    date: dayjs('9999-12-31'),
                    exerciseId: null,
                },
                {
                    pageIndex: 2,
                    date: dayjs('9999-12-31'),
                    exerciseId: null,
                },
            ]);
        });

        it('should emit hidden pages with selected calendar date', () => {
            const futureDate = new Date();
            futureDate.setFullYear(futureDate.getFullYear() + 1);
            const testDate = futureDate.toISOString().slice(0, 16); // Format as YYYY-MM-DDTHH:MM

            component.calendarSelected.set(true);
            component.defaultDate.set(testDate);
            fixture.componentRef.setInput('pageIndices', [3]);

            component.onSubmit();

            expect(alertServiceMock.error).not.toHaveBeenCalled();
            expect(hiddenPagesOutputSpy).toHaveBeenCalledWith([
                {
                    pageIndex: 3,
                    date: dayjs(testDate),
                    exerciseId: null,
                },
            ]);
        });

        it('should emit hidden pages with selected exercise due date and id', () => {
            const futureDate = dayjs().add(1, 'year');
            const futureExercise = {
                id: 123,
                type: ExerciseType.QUIZ,
                dueDate: futureDate,
            } as Exercise;

            component.exerciseSelected.set(true);
            component.selectedExercise.set(futureExercise);
            fixture.componentRef.setInput('pageIndices', [5, 6]);

            component.onSubmit();

            expect(hiddenPagesOutputSpy).toHaveBeenCalledWith([
                {
                    pageIndex: 5,
                    date: futureDate,
                    exerciseId: 123,
                },
                {
                    pageIndex: 6,
                    date: futureDate,
                    exerciseId: 123,
                },
            ]);
        });

        it('should not emit when no valid date option is selected', () => {
            component.onSubmit();
            expect(hiddenPagesOutputSpy).not.toHaveBeenCalled();
        });

        it('should show error and not emit when calendar date is in the past', () => {
            // Use a past date that will fail validation
            const pastDate = '2020-01-01T10:00';
            component.calendarSelected.set(true);
            component.defaultDate.set(pastDate);

            component.onSubmit();

            expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.attachment.pdfPreview.dateBox.dateError');
            expect(hiddenPagesOutputSpy).not.toHaveBeenCalled();
        });
    });

    describe('Computed Properties', () => {
        it('should correctly sort and format page indices', () => {
            fixture.componentRef.setInput('pageIndices', [3, 1, 2]);
            expect(component.pageIndicesSorted()).toBe('1, 2, 3');
        });
    });
});

import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import dayjs from 'dayjs/esm';
import { PdfPreviewDateBoxComponent } from '../../../../../../main/webapp/app/lecture/pdf-preview/pdf-preview-date-box/pdf-preview-date-box.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';

describe('PdfPreviewDateBoxComponent', () => {
    let component: PdfPreviewDateBoxComponent;
    let fixture: ComponentFixture<PdfPreviewDateBoxComponent>;
    let courseExerciseServiceMock: any;

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

        await TestBed.configureTestingModule({
            imports: [PdfPreviewDateBoxComponent],
            providers: [
                { provide: CourseExerciseService, useValue: courseExerciseServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(PdfPreviewDateBoxComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('course', mockCourse);
        fixture.componentRef.setInput('pageIndex', 1);

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
    });

    describe('Exercise Processing', () => {
        it('should correctly categorize and sort exercises', fakeAsync(() => {
            component.ngOnInit();
            tick();

            const categorized = component.categorizedExercises();
            expect(categorized).toHaveLength(2); // QUIZ and PROGRAMMING

            // Check QUIZ exercises
            expect(categorized[0].type).toBe(ExerciseType.QUIZ);
            expect(categorized[0].exercises).toHaveLength(2);
            expect(categorized[0].exercises[0].id).toBe(1); // Earlier date first

            // Check PROGRAMMING exercises
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
        let hiddenPageOutputSpy: jest.SpyInstance;

        beforeEach(() => {
            hiddenPageOutputSpy = jest.spyOn(component.hiddenPageOutput, 'emit');
        });

        it('should emit hidden page with forever date when hide forever is selected', () => {
            component.hideForever.set(true);
            component.onSubmit();

            expect(hiddenPageOutputSpy).toHaveBeenCalledWith({
                pageIndex: 1,
                date: dayjs('9999-12-31'),
            });
        });

        it('should emit hidden page with calendar date when calendar is selected', () => {
            const testDate = '2024-02-01T10:00';
            component.calendarSelected.set(true);
            component.defaultDate.set(testDate);

            component.onSubmit();

            expect(hiddenPageOutputSpy).toHaveBeenCalledWith({
                pageIndex: 1,
                date: dayjs(testDate),
            });
        });

        it('should emit hidden page with exercise due date when exercise is selected', () => {
            component.exerciseSelected.set(true);
            component.selectedExercise.set(mockExercises[0]);

            component.onSubmit();

            expect(hiddenPageOutputSpy).toHaveBeenCalledWith({
                pageIndex: 1,
                date: mockExercises[0].dueDate,
            });
        });

        it('should not emit when no valid date option is selected', () => {
            component.onSubmit();
            expect(hiddenPageOutputSpy).not.toHaveBeenCalled();
        });
    });
});

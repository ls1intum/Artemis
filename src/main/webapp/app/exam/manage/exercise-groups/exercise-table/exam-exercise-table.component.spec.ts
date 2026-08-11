import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamExerciseTableComponent } from 'app/exam/manage/exercise-groups/exercise-table/exam-exercise-table.component';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';
import { ProgrammingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/programming-exercise-cell/programming-exercise-group-cell.component';
import { QuizExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/quiz-exercise-cell/quiz-exercise-group-cell.component';
import { ModelingExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/modeling-exercise-cell/modeling-exercise-group-cell.component';
import { FileUploadExerciseGroupCellComponent } from 'app/exam/manage/exercise-groups/file-upload-exercise-cell/file-upload-exercise-group-cell.component';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';

@Component({ selector: 'jhi-exam-exercise-row-buttons', template: '' })
class ExamExerciseRowButtonsStubComponent {
    readonly course = input.required<Course>();
    readonly exercise = input.required<Exercise>();
    readonly exam = input.required<Exam>();
    readonly exerciseGroupId = input.required<number>();
    readonly latestIndividualEndDate = input<unknown>(undefined);
    readonly onDeleteExercise = output<void>();
    readonly actionsMinWidth = output<number>();
}

@Component({ selector: 'jhi-programming-exercise-group-cell', template: '' })
class ProgrammingCellStubComponent {
    readonly exercise = input.required<Exercise>();
    readonly displayShortName = input(false);
    readonly displayTemplateUrls = input(false);
    readonly displayEditorMode = input(false);
}

@Component({ selector: 'jhi-quiz-exercise-group-cell', template: '' })
class QuizCellStubComponent {
    readonly exercise = input.required<Exercise>();
}

@Component({ selector: 'jhi-modeling-exercise-group-cell', template: '' })
class ModelingCellStubComponent {
    readonly exercise = input.required<Exercise>();
}

@Component({ selector: 'jhi-file-upload-exercise-group-cell', template: '' })
class FileUploadCellStubComponent {
    readonly exercise = input.required<Exercise>();
}

describe('ExamExerciseTableComponent', () => {
    let component: ExamExerciseTableComponent;
    let fixture: ComponentFixture<ExamExerciseTableComponent>;

    const group1: ExerciseGroup = { id: 1, title: 'Group 1' };
    const group2: ExerciseGroup = { id: 2, title: 'Group 2' };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamExerciseTableComponent],
            providers: [provideRouter([]), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExamExerciseTableComponent, {
                remove: {
                    imports: [
                        ExamExerciseRowButtonsComponent,
                        ProgrammingExerciseGroupCellComponent,
                        QuizExerciseGroupCellComponent,
                        ModelingExerciseGroupCellComponent,
                        FileUploadExerciseGroupCellComponent,
                    ],
                },
                add: {
                    imports: [ExamExerciseRowButtonsStubComponent, ProgrammingCellStubComponent, QuizCellStubComponent, ModelingCellStubComponent, FileUploadCellStubComponent],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExamExerciseTableComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exercises', []);
        fixture.componentRef.setInput('group', group1);
        fixture.componentRef.setInput('groups', [group1, group2]);
        fixture.componentRef.setInput('course', { id: 1 } as Course);
        fixture.componentRef.setInput('exam', { id: 2 } as Exam);
        fixture.componentRef.setInput('courseId', 1);
        fixture.componentRef.setInput('examId', 2);
    });

    it('shows type-specific columns only when a matching exercise is present', () => {
        fixture.componentRef.setInput('exercises', [{ id: 1, type: ExerciseType.QUIZ } as Exercise]);
        fixture.detectChanges();

        expect(component['hasQuiz']()).toBe(true);
        expect(component['hasProgramming']()).toBe(false);
        expect(component['hasModeling']()).toBe(false);
        expect(component['hasFileUpload']()).toBe(false);
        expect(component['hasAssessmentModeColumn']()).toBe(false);
    });

    it('shows the assessment-mode column for programming, modeling, and text exercises', () => {
        for (const type of [ExerciseType.PROGRAMMING, ExerciseType.MODELING, ExerciseType.TEXT]) {
            fixture.componentRef.setInput('exercises', [{ id: 1, type } as Exercise]);
            fixture.detectChanges();
            expect(component['hasAssessmentModeColumn']()).toBe(true);
        }
        fixture.componentRef.setInput('exercises', [{ id: 1, type: ExerciseType.QUIZ } as Exercise]);
        fixture.detectChanges();
        expect(component['hasAssessmentModeColumn']()).toBe(false);
    });

    it('only shows the drag handle with more than one group', () => {
        fixture.componentRef.setInput('groups', [group1]);
        fixture.detectChanges();
        expect(component['showDragHandle']()).toBe(false);

        fixture.componentRef.setInput('groups', [group1, group2]);
        fixture.detectChanges();
        expect(component['showDragHandle']()).toBe(true);
    });

    it('builds group dropdown options from the groups input, falling back to #id when untitled', () => {
        fixture.componentRef.setInput('groups', [group1, { id: 3 } as ExerciseGroup]);
        fixture.detectChanges();

        expect(component['groupOptions']()).toEqual([
            { label: 'Group 1', value: 1 },
            { label: '#3', value: 3 },
        ]);
    });

    it('builds the exam-scoped title link for an exercise', () => {
        fixture.detectChanges();
        expect(component.titleLink({ id: 42, type: ExerciseType.TEXT } as Exercise)).toEqual(['/course-management', 1, 'exams', 2, 'exercise-groups', 1, 'text-exercises', 42]);
    });

    it('emits groupChange only when a different group is selected', () => {
        fixture.detectChanges();
        const changes: unknown[] = [];
        component.groupChange.subscribe((event) => changes.push(event));

        component.onGroupSelect({ id: 5 } as Exercise, group1.id);
        expect(changes).toHaveLength(0);

        component.onGroupSelect({ id: 5 } as Exercise, group2.id);
        expect(changes).toEqual([{ exercise: { id: 5 }, group: group2 }]);
    });

    it('emits groupChange only for cross-container drops', () => {
        fixture.detectChanges();
        const changes: unknown[] = [];
        component.groupChange.subscribe((event) => changes.push(event));
        const exercise = { id: 7 } as Exercise;

        const sharedContainer = { id: 'a' };
        const sameContainerEvent = { previousContainer: sharedContainer, container: sharedContainer, item: { data: exercise } } as unknown as CdkDragDrop<Exercise[]>;
        component.onDrop(sameContainerEvent);
        expect(changes).toHaveLength(0);

        const crossContainerEvent = { previousContainer: sharedContainer, container: { id: 'b' }, item: { data: exercise } } as unknown as CdkDragDrop<Exercise[]>;
        component.onDrop(crossContainerEvent);
        expect(changes).toEqual([{ exercise, group: group1 }]);
    });

    it('reports disabled exercise types', () => {
        fixture.componentRef.setInput('disabledExerciseTypes', [ExerciseType.MODELING]);
        fixture.detectChanges();

        expect(component.isExerciseTypeDisabled({ type: ExerciseType.MODELING } as Exercise)).toBe(true);
        expect(component.isExerciseTypeDisabled({ type: ExerciseType.TEXT } as Exercise)).toBe(false);
    });
});

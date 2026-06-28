import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ExerciseGroupTimelineLockComponent } from 'app/core/course/manage/exercises-experimental/group-timeline-lock/exercise-group-timeline-lock.component';
import { ExerciseGroupEditModalComponent } from 'app/core/course/manage/exercises-experimental/group-edit-modal/exercise-group-edit-modal.component';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/core/course/manage/exercises/exercise-variant-group.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseExerciseGroup } from 'app/core/course/manage/exercises/course-exercise-group.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ExerciseGroupTimelineLockComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseGroupTimelineLockComponent>;
    let component: ExerciseGroupTimelineLockComponent;
    let service: ExerciseVariantGroupService;
    let dialogService: DialogService;
    let onCloseSubject: Subject<CourseExerciseGroup | undefined>;

    const buildExercise = (groupId?: number): TextExercise => {
        const course = { id: 42 } as Course;
        const exercise = new TextExercise(course, undefined);
        exercise.id = 7;
        if (groupId !== undefined) {
            exercise.exerciseVariantGroup = { id: groupId, title: 'Group A', releaseDate: dayjs('2026-01-01T00:00:00Z') };
        }
        return exercise;
    };

    beforeEach(async () => {
        onCloseSubject = new Subject<CourseExerciseGroup | undefined>();
        await TestBed.configureTestingModule({
            imports: [ExerciseGroupTimelineLockComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(TranslateService), MockProvider(DialogService)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupTimelineLockComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExerciseVariantGroupService);
        dialogService = TestBed.inject(DialogService);
        vi.spyOn(dialogService, 'open').mockReturnValue({ onClose: onCloseSubject.asObservable() } as DynamicDialogRef);
    });

    it('is locked only when the exercise belongs to a variant group', () => {
        fixture.componentRef.setInput('exercise', buildExercise(undefined));
        fixture.detectChanges();
        expect(component.locked()).toBe(false);

        fixture.componentRef.setInput('exercise', buildExercise(3));
        fixture.detectChanges();
        expect(component.locked()).toBe(true);
    });

    it('opens the group-edit dialog only when locked', () => {
        fixture.componentRef.setInput('exercise', buildExercise(undefined));
        fixture.detectChanges();
        component.openModal();
        expect(dialogService.open).not.toHaveBeenCalled();

        fixture.componentRef.setInput('exercise', buildExercise(3));
        fixture.detectChanges();
        component.openModal();
        expect(dialogService.open).toHaveBeenCalledOnce();
        expect(dialogService.open).toHaveBeenCalledWith(ExerciseGroupEditModalComponent, expect.objectContaining({ inputValues: { group: component.group() }, modal: true }));
    });

    it('derives the modal group from the embedded reference', () => {
        fixture.componentRef.setInput('exercise', buildExercise(3));
        fixture.detectChanges();
        const group = component.group();
        expect(group.id).toBe(3);
        expect(group.title).toBe('Group A');
        expect(group.exercises).toEqual([]);
    });

    it('persists the group and emits the exercise with the group timeline applied when the dialog closes with a result', () => {
        const exercise = buildExercise(3);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        const dto: ExerciseVariantGroupDTO = {
            id: 3,
            title: 'Group A',
            releaseDate: dayjs('2026-02-02T00:00:00Z'),
            dueDate: dayjs('2026-03-03T00:00:00Z'),
        };
        const updateSpy = vi.spyOn(service, 'updateGroup').mockReturnValue(of(dto));
        const emitted: TextExercise[] = [];
        component.exerciseChange.subscribe((value) => emitted.push(value as TextExercise));

        component.openModal();
        const edited: CourseExerciseGroup = { id: 3, title: 'Group A', releaseDate: dto.releaseDate, dueDate: dto.dueDate, exercises: [] };
        onCloseSubject.next(edited);

        expect(updateSpy).toHaveBeenCalledOnce();
        expect(updateSpy.mock.calls[0][0]).toBe(42);
        expect(emitted).toHaveLength(1);
        const result = emitted[0];
        expect(result).not.toBe(exercise);
        expect(result.releaseDate?.toISOString()).toBe(dto.releaseDate!.toISOString());
        expect(result.dueDate?.toISOString()).toBe(dto.dueDate!.toISOString());
        expect(result.exerciseVariantGroup?.id).toBe(3);
    });

    it('does nothing when the dialog is cancelled (closed without a result)', () => {
        fixture.componentRef.setInput('exercise', buildExercise(3));
        fixture.detectChanges();
        const updateSpy = vi.spyOn(service, 'updateGroup');

        component.openModal();
        onCloseSubject.next(undefined);

        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('does not persist when the course id cannot be resolved', () => {
        const exercise = buildExercise(3);
        exercise.course = undefined;
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        const updateSpy = vi.spyOn(service, 'updateGroup');
        component.onSave({ id: 3, title: 'Group A', exercises: [] });
        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('surfaces an error alert when the update fails', () => {
        const exercise = buildExercise(3);
        fixture.componentRef.setInput('exercise', exercise);
        fixture.detectChanges();

        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'addErrorAlert').mockImplementation(() => undefined);
        vi.spyOn(service, 'updateGroup').mockReturnValue(throwError(() => ({ message: 'boom', error: {} })));

        component.onSave({ id: 3, title: 'Group A', exercises: [] });
        expect(alertSpy).toHaveBeenCalled();
    });
});

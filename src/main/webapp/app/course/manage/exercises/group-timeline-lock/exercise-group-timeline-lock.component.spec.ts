import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ExerciseGroupTimelineLockComponent } from 'app/course/manage/exercises/group-timeline-lock/exercise-group-timeline-lock.component';
import { ExerciseVariantGroupDTO, ExerciseVariantGroupService } from 'app/course/manage/exercises/exercise-variant-group.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ExerciseGroupTimelineLockComponent', () => {
    let fixture: ComponentFixture<ExerciseGroupTimelineLockComponent>;
    let component: ExerciseGroupTimelineLockComponent;
    let service: ExerciseVariantGroupService;

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
        await TestBed.configureTestingModule({
            imports: [ExerciseGroupTimelineLockComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), MockProvider(TranslateService)],
        })
            // Render nothing: these are logic tests, and the real declarative modal pulls in heavy deps.
            .overrideTemplate(ExerciseGroupTimelineLockComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupTimelineLockComponent);
        component = fixture.componentInstance;
        service = TestBed.inject(ExerciseVariantGroupService);
    });

    it('is locked only when the exercise belongs to a variant group', () => {
        fixture.componentRef.setInput('exercise', buildExercise(undefined));
        expect(component.locked()).toBe(false);

        fixture.componentRef.setInput('exercise', buildExercise(3));
        expect(component.locked()).toBe(true);
    });

    it('shows the group-edit modal only when locked', () => {
        fixture.componentRef.setInput('exercise', buildExercise(undefined));
        component.openModal();
        expect(component.showModal()).toBe(false);

        fixture.componentRef.setInput('exercise', buildExercise(3));
        component.openModal();
        expect(component.showModal()).toBe(true);
    });

    it('derives the modal group from the embedded reference', () => {
        fixture.componentRef.setInput('exercise', buildExercise(3));
        const group = component.group();
        expect(group.id).toBe(3);
        expect(group.title).toBe('Group A');
        expect(group.exercises).toEqual([]);
    });

    it('persists the group and emits the exercise with the group timeline applied on save', () => {
        const exercise = buildExercise(3);
        fixture.componentRef.setInput('exercise', exercise);

        const dto: ExerciseVariantGroupDTO = {
            id: 3,
            title: 'Group A',
            releaseDate: dayjs('2026-02-02T00:00:00Z'),
            dueDate: dayjs('2026-03-03T00:00:00Z'),
        };
        const updateSpy = vi.spyOn(service, 'updateGroup').mockReturnValue(of(dto));
        const emitted: TextExercise[] = [];
        component.exerciseChange.subscribe((value) => emitted.push(value as TextExercise));

        // The modal's (saved) output calls onSave with the edited group.
        const edited: CourseExerciseGroup = { id: 3, title: 'Group A', releaseDate: dto.releaseDate, dueDate: dto.dueDate, exercises: [] };
        component.onSave(edited);

        expect(updateSpy).toHaveBeenCalledOnce();
        expect(updateSpy.mock.calls[0][0]).toBe(42);
        expect(emitted).toHaveLength(1);
        const result = emitted[0];
        expect(result).not.toBe(exercise);
        expect(result.releaseDate?.toISOString()).toBe(dto.releaseDate!.toISOString());
        expect(result.dueDate?.toISOString()).toBe(dto.dueDate!.toISOString());
        expect(result.exerciseVariantGroup?.id).toBe(3);
    });

    it('does not persist when the course id cannot be resolved', () => {
        const exercise = buildExercise(3);
        exercise.course = undefined;
        fixture.componentRef.setInput('exercise', exercise);

        const updateSpy = vi.spyOn(service, 'updateGroup');
        component.onSave({ id: 3, title: 'Group A', exercises: [] });
        expect(updateSpy).not.toHaveBeenCalled();
    });

    it('surfaces an error alert when the update fails', () => {
        const exercise = buildExercise(3);
        fixture.componentRef.setInput('exercise', exercise);

        const alertService = TestBed.inject(AlertService);
        const alertSpy = vi.spyOn(alertService, 'addErrorAlert').mockImplementation(() => undefined);
        vi.spyOn(service, 'updateGroup').mockReturnValue(throwError(() => ({ message: 'boom', error: {} })));

        component.onSave({ id: 3, title: 'Group A', exercises: [] });
        expect(alertSpy).toHaveBeenCalled();
    });
});

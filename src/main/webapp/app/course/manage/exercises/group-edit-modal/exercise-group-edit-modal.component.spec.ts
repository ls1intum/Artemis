import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { TumUiButtonComponent, TumUiDialogComponent, TumUiInputDirective, TumUiInputNumberComponent, TumUiMessageComponent, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/exercise/exercise-timeline-stub.component';
import { ExerciseGroupEditModalComponent } from 'app/course/manage/exercises/group-edit-modal/exercise-group-edit-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseGroupEditModalComponent', () => {
    let fixture: ComponentFixture<ExerciseGroupEditModalComponent>;
    let component: ExerciseGroupEditModalComponent;

    const buildGroup = (overrides?: Partial<CourseExerciseGroup>): CourseExerciseGroup => ({
        id: 1,
        title: 'Group A',
        maxPoints: 10,
        releaseDate: dayjs('2026-01-01T00:00:00Z'),
        startDate: dayjs('2026-01-02T00:00:00Z'),
        dueDate: dayjs('2026-01-10T00:00:00Z'),
        assessmentDueDate: dayjs('2026-01-15T00:00:00Z'),
        exampleSolutionPublicationDate: dayjs('2026-01-20T00:00:00Z'),
        exercises: [{ id: 5, type: ExerciseType.TEXT } as Exercise],
        ...overrides,
    });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseGroupEditModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseGroupEditModalComponent, {
                set: {
                    imports: [
                        FormsModule,
                        TumUiDialogComponent,
                        TumUiInputDirective,
                        TumUiInputNumberComponent,
                        TumUiButtonComponent,
                        TumUiMessageComponent,
                        TumUiTooltipDirective,
                        FaIconComponent,
                        ArtemisTranslatePipe,
                        TranslateDirective,
                        ExerciseTimelineStubComponent,
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseGroupEditModalComponent);
        component = fixture.componentInstance;
    });

    it('initializes the drafts from the input group', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();

        expect(component.draftTitle()).toBe('Group A');
        expect(component.draftMaxPoints()).toBe(10);
        expect(component.draftReleaseDate()?.isSame(dayjs('2026-01-01T00:00:00Z'))).toBe(true);
        expect(component.draftDueDate()?.isSame(dayjs('2026-01-10T00:00:00Z'))).toBe(true);
    });

    it('coerces dates that arrive as ISO strings into dayjs objects', () => {
        fixture.componentRef.setInput('group', buildGroup({ releaseDate: '2026-03-03T00:00:00Z' as unknown as dayjs.Dayjs }));
        fixture.detectChanges();

        const releaseDate = component.draftReleaseDate();
        expect(releaseDate?.isValid()).toBe(true);
        expect(releaseDate?.isSame(dayjs('2026-03-03T00:00:00Z'))).toBe(true);
    });

    it('titles the dialog "create" for a new group and "edit" for an existing one', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();
        expect(component.headerStringKey()).toBe('artemisApp.exerciseManagement.groupEdit.header');

        fixture.componentRef.setInput('isNew', true);
        fixture.detectChanges();
        expect(component.headerStringKey()).toBe('artemisApp.exerciseManagement.groupEdit.createHeader');
    });

    it('marks the title invalid when blank and disables save', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();

        component.draftTitle.set('   ');
        expect(component.isTitleValid()).toBe(false);
        expect(component.isSaveDisabled()).toBe(true);
    });

    it('marks the title invalid when longer than 255 characters and disables save', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();

        component.draftTitle.set('x'.repeat(256));
        expect(component.isTitleValid()).toBe(false);
        expect(component.isSaveDisabled()).toBe(true);

        component.draftTitle.set('x'.repeat(255));
        expect(component.isTitleValid()).toBe(true);
        expect(component.isSaveDisabled()).toBe(false);
    });

    // The build-and-test date is derived per programming exercise from its own build plan, so no single group value can be
    // correct for every member. It is therefore not part of the shared group timeline and must never be offered here.
    it('never offers the build-and-test date, not even for a group with a programming member', () => {
        for (const exercises of [[{ id: 5, type: ExerciseType.TEXT } as Exercise], [{ id: 6, type: ExerciseType.PROGRAMMING } as Exercise], undefined]) {
            fixture.componentRef.setInput('group', buildGroup({ exercises }));
            fixture.detectChanges();
            expect(component.timelineItems().map((item) => item.labelStringKey)).not.toContain('artemisApp.exercise.dateForRunningTestsAfterDueDate');
        }
    });

    it('closes without emitting saved when saving without any changes', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);

        component.onSave();

        expect(savedSpy).not.toHaveBeenCalled();
        expect(component.visible()).toBe(false);
    });

    it('emits the updated group and closes when a field changed', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        const saved: unknown[] = [];
        component.saved.subscribe((group) => saved.push(group));

        component.draftTitle.set('Renamed group');
        component.onSave();

        expect(saved).toEqual([expect.objectContaining({ title: 'Renamed group' })]);
        expect(component.visible()).toBe(false);
    });

    it('closes without emitting saved on cancel', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        const savedSpy = vi.fn();
        component.saved.subscribe(savedSpy);

        component.onCancel();

        expect(savedSpy).not.toHaveBeenCalled();
        expect(component.visible()).toBe(false);
    });
});

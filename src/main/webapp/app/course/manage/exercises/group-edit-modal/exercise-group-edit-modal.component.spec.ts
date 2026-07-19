import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExerciseTimelineStubComponent } from 'test/helpers/stubs/modeling/exercise-timeline-stub.component';
import { ExerciseGroupEditModalComponent } from 'app/course/manage/exercises/group-edit-modal/exercise-group-edit-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { CourseExerciseGroup } from 'app/exercise/shared/entities/exercise/course-exercise-group.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';

describe('ExerciseGroupEditModalComponent', () => {
    let fixture: ComponentFixture<ExerciseGroupEditModalComponent>;
    let component: ExerciseGroupEditModalComponent;
    let dialogRef: DynamicDialogRef;

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
            providers: [MockProvider(DynamicDialogRef), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseGroupEditModalComponent, {
                set: {
                    imports: [
                        FormsModule,
                        InputTextModule,
                        InputNumberModule,
                        ButtonModule,
                        TooltipModule,
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
        dialogRef = TestBed.inject(DynamicDialogRef);
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

    it('includes the build-and-test date only when the group has a programming member', () => {
        fixture.componentRef.setInput('group', buildGroup({ exercises: [{ id: 5, type: ExerciseType.TEXT } as Exercise] }));
        fixture.detectChanges();
        expect(component.timelineItems().map((item) => item.labelStringKey)).not.toContain('artemisApp.exercise.dateForRunningTestsAfterDueDate');

        fixture.componentRef.setInput('group', buildGroup({ exercises: [{ id: 6, type: ExerciseType.PROGRAMMING } as Exercise] }));
        fixture.detectChanges();
        expect(component.timelineItems().map((item) => item.labelStringKey)).toContain('artemisApp.exercise.dateForRunningTestsAfterDueDate');
    });

    it('includes the build-and-test date when membership is unknown', () => {
        fixture.componentRef.setInput('group', buildGroup({ exercises: undefined }));
        fixture.detectChanges();
        expect(component.timelineItems().map((item) => item.labelStringKey)).toContain('artemisApp.exercise.dateForRunningTestsAfterDueDate');
    });

    it('closes the dialog with undefined when saving without any changes', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.onSave();

        expect(closeSpy).toHaveBeenCalledWith(undefined);
    });

    it('closes the dialog with the updated group when a field changed', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.draftTitle.set('Renamed group');
        component.onSave();

        expect(closeSpy).toHaveBeenCalledWith(expect.objectContaining({ title: 'Renamed group' }));
    });

    it('closes the dialog with no result on cancel', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();
        const closeSpy = vi.spyOn(dialogRef, 'close');

        component.onCancel();

        expect(closeSpy).toHaveBeenCalledWith();
    });
});

import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiDialogComponent, TumUiInputDirective } from '@tumaet/ui-angular';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';

import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ExamExerciseGroupEditModalComponent } from 'app/exam/manage/exercise-groups/group-edit-modal/exam-exercise-group-edit-modal.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';

describe('ExamExerciseGroupEditModalComponent', () => {
    let fixture: ComponentFixture<ExamExerciseGroupEditModalComponent>;
    let component: ExamExerciseGroupEditModalComponent;

    const buildGroup = (overrides?: Partial<ExerciseGroup>): ExerciseGroup => ({ id: 1, title: 'Group A', isMandatory: true, ...overrides });

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamExerciseGroupEditModalComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExamExerciseGroupEditModalComponent, {
                set: { imports: [FormsModule, TumUiDialogComponent, TumUiInputDirective, TumUiButtonComponent, TumUiCheckboxComponent, ArtemisTranslatePipe, TranslateDirective] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExamExerciseGroupEditModalComponent);
        component = fixture.componentInstance;
    });

    it('initializes the drafts from the input group', () => {
        fixture.componentRef.setInput('group', buildGroup({ isMandatory: false }));
        fixture.detectChanges();

        expect(component.draftTitle()).toBe('Group A');
        expect(component.draftIsMandatory()).toBe(false);
    });

    it('marks the title invalid when blank', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();

        component.draftTitle.set('   ');
        expect(component.isTitleValid()).toBe(false);
    });

    it('marks the title invalid when longer than 255 characters', () => {
        fixture.componentRef.setInput('group', buildGroup());
        fixture.detectChanges();

        component.draftTitle.set('x'.repeat(256));
        expect(component.isTitleValid()).toBe(false);

        component.draftTitle.set('x'.repeat(255));
        expect(component.isTitleValid()).toBe(true);
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

    it('emits the updated title and closes when the title changed', () => {
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

    it('emits the updated mandatory flag when only that changed', () => {
        fixture.componentRef.setInput('group', buildGroup({ isMandatory: true }));
        fixture.componentRef.setInput('visible', true);
        fixture.detectChanges();
        const saved: unknown[] = [];
        component.saved.subscribe((group) => saved.push(group));

        component.draftIsMandatory.set(false);
        component.onSave();

        expect(saved).toEqual([expect.objectContaining({ title: 'Group A', isMandatory: false })]);
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

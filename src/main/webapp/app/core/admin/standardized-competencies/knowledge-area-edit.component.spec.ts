/**
 * Vitest tests for KnowledgeAreaEditComponent.
 * Tests the form editing functionality for knowledge areas including
 * validation, save, cancel, and circular dependency prevention.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ReactiveFormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { KnowledgeAreaEditComponent } from 'app/core/admin/standardized-competencies/knowledge-area-edit.component';
import { KnowledgeAreaDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

describe('KnowledgeAreaEditComponent', () => {
    setupTestBed({ zoneless: true });

    let componentFixture: ComponentFixture<KnowledgeAreaEditComponent>;
    let component: KnowledgeAreaEditComponent;

    /** Default knowledge area for testing */
    const defaultKnowledgeArea: KnowledgeAreaDTO = {
        id: 1,
        title: 'title',
        shortTitle: 'title',
        description: 'description',
        parentId: 1,
    };

    /** New values for form updates */
    const newValues = {
        title: 'new title',
        shortTitle: 'new title',
        description: 'new description',
        parentId: 2,
    };

    /** Available knowledge areas for parent selection */
    const defaultKnowledgeAreas: KnowledgeAreaDTO[] = [
        { id: 1, title: 'KA1' },
        { id: 2, title: 'KA2' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, KnowledgeAreaEditComponent],
            providers: [],
        })
            .overrideComponent(KnowledgeAreaEditComponent, {
                set: {
                    imports: [
                        ReactiveFormsModule,
                        FaIconComponent,
                        MockComponent(ButtonComponent),
                        MockPipe(HtmlForMarkdownPipe),
                        MockComponent(MarkdownEditorMonacoComponent),
                        MockDirective(TranslateDirective),
                        MockDirective(DeleteButtonDirective),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(KnowledgeAreaEditComponent);
                component = componentFixture.componentInstance;
                // Use componentRef.setInput() for signal inputs
                componentFixture.componentRef.setInput('knowledgeArea', defaultKnowledgeArea);
                componentFixture.componentRef.setInput('knowledgeAreas', defaultKnowledgeAreas);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should set form values to knowledgeArea', () => {
        componentFixture.detectChanges();
        compareFormValues(component.form.getRawValue(), defaultKnowledgeArea);
    });

    it('should disable/enable when setting edit mode', () => {
        componentFixture.componentRef.setInput('isEditing', false);
        componentFixture.detectChanges();
        expect(component.form.disabled).toBe(true);

        component.edit();
        componentFixture.detectChanges();
        expect(component.form.disabled).toBe(false);
    });

    it('should save', () => {
        componentFixture.componentRef.setInput('isEditing', true);
        componentFixture.detectChanges();
        component.form.setValue(newValues);
        const saveSpy = vi.spyOn(component.onSave, 'emit');

        component.save();

        expect(saveSpy).toHaveBeenCalledWith({ ...defaultKnowledgeArea, ...newValues });
        expect(component.isEditing()).toBe(false);
    });

    it.each<[KnowledgeAreaDTO, boolean]>([
        [defaultKnowledgeArea, false],
        [{ title: 'new knowledgeArea', shortTitle: 'new ka' } as KnowledgeAreaDTO, true],
    ])('should cancel and close', (knowledgeArea, shouldClose) => {
        componentFixture.componentRef.setInput('knowledgeArea', knowledgeArea);
        componentFixture.componentRef.setInput('isEditing', true);
        componentFixture.detectChanges();
        component.form.setValue(newValues);
        const closeSpy = vi.spyOn(component.onClose, 'emit');

        component.cancel();

        compareFormValues(component.form.getRawValue(), knowledgeArea);
        expect(component.isEditing()).toBe(false);
        if (shouldClose) {
            expect(closeSpy).toHaveBeenCalled();
        } else {
            expect(closeSpy).not.toHaveBeenCalled();
        }
    });

    it('should delete', () => {
        componentFixture.detectChanges();
        const deleteSpy = vi.spyOn(component.onDelete, 'emit');
        component.delete();

        expect(deleteSpy).toHaveBeenCalled();
    });

    it('should close', () => {
        const closeSpy = vi.spyOn(component.onClose, 'emit');
        component.close();

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should open new knowledge area', () => {
        componentFixture.detectChanges();
        const openSpy = vi.spyOn(component.onOpenNewKnowledgeArea, 'emit');
        component.openNewKnowledgeArea();

        expect(openSpy).toHaveBeenCalled();
    });

    it('should open new competency', () => {
        componentFixture.detectChanges();
        const openSpy = vi.spyOn(component.onOpenNewCompetency, 'emit');
        component.openNewCompetency();

        expect(openSpy).toHaveBeenCalled();
    });

    it('should update description', () => {
        componentFixture.detectChanges();
        component.updateDescriptionControl('new description');

        expect(component.form.controls.description.getRawValue()).toBe('new description');
    });

    it.each<[number, boolean]>([
        [1, true],
        [2, true],
        [3, false],
    ])('should not allow circular dependencies', (newParentId, isInvalid) => {
        // Only knowledge area 3 is ok because it is not a descendant of knowledge area 1
        componentFixture.componentRef.setInput('knowledgeArea', {
            id: 1,
            title: 'KA1',
            shortTitle: 'title',
            children: [{ id: 2 }],
            parentId: 99,
        });
        componentFixture.componentRef.setInput('knowledgeAreas', [{ id: 1 }, { id: 2 }, { id: 3 }]);
        componentFixture.componentRef.setInput('isEditing', true);
        componentFixture.detectChanges();
        expect(component.form.controls.parentId.invalid).toBe(false);

        const select = componentFixture.debugElement.query(By.css('#knowledge-area-select')).nativeElement;
        select.value = select.options[newParentId].value;
        select.dispatchEvent(new Event('change'));
        componentFixture.detectChanges();

        expect(component.form.controls.parentId.value).toEqual(newParentId);
        expect(component.form.controls.parentId.invalid).toEqual(isInvalid);
    });

    /**
     * Helper to compare form values with knowledge area properties.
     */
    function compareFormValues(formValues: any, knowledgeArea: KnowledgeAreaDTO) {
        for (const key in formValues) {
            // Normalize null to undefined for comparison
            expect(formValues[key] ?? undefined).toEqual(knowledgeArea[key as keyof KnowledgeAreaDTO]);
        }
    }
});

/**
 * Vitest tests for StandardizedCompetencyEditComponent.
 * Tests the form editing functionality for standardized competencies
 * including form initialization, save, cancel, and validation.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ReactiveFormsModule } from '@angular/forms';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import { StandardizedCompetencyEditComponent } from 'app/core/admin/standardized-competencies/standardized-competency-edit.component';
import { KnowledgeAreaDTO, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';

describe('StandardizedCompetencyEditComponent', () => {
    setupTestBed({ zoneless: true });

    let componentFixture: ComponentFixture<StandardizedCompetencyEditComponent>;
    let component: StandardizedCompetencyEditComponent;

    /** Default competency for testing */
    const defaultCompetency: StandardizedCompetencyDTO = {
        id: 1,
        title: 'title',
        description: 'description',
        taxonomy: CompetencyTaxonomy.ANALYZE,
        knowledgeAreaId: 1,
        sourceId: 1,
        version: '1.0.0',
    };

    /** New values for form updates */
    const newValues = {
        title: 'new title',
        description: 'new description',
        taxonomy: CompetencyTaxonomy.APPLY,
        knowledgeAreaId: 2,
        sourceId: 2,
    };

    /** Available knowledge areas for selection */
    const defaultKnowledgeAreas: KnowledgeAreaDTO[] = [
        { id: 1, title: 'KA1' },
        { id: 2, title: 'KA2' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, StandardizedCompetencyEditComponent],
            providers: [],
        })
            .overrideComponent(StandardizedCompetencyEditComponent, {
                set: {
                    imports: [
                        ReactiveFormsModule,
                        FaIconComponent,
                        MockComponent(ButtonComponent),
                        MockPipe(HtmlForMarkdownPipe),
                        MockComponent(MarkdownEditorMonacoComponent),
                        MockComponent(TaxonomySelectComponent),
                        MockDirective(TranslateDirective),
                        MockDirective(DeleteButtonDirective),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyEditComponent);
                component = componentFixture.componentInstance;
                // Use componentRef.setInput() for signal inputs
                componentFixture.componentRef.setInput('competency', defaultCompetency);
                componentFixture.componentRef.setInput('knowledgeAreas', defaultKnowledgeAreas);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should set form values to competency', () => {
        componentFixture.detectChanges();
        compareFormValues(component['form'].getRawValue(), defaultCompetency);
    });

    it('should disable/enable when setting edit mode', () => {
        componentFixture.componentRef.setInput('isEditing', false);
        componentFixture.detectChanges();
        expect(component['form'].disabled).toBe(true);

        component.edit();
        componentFixture.detectChanges();
        expect(component['form'].disabled).toBe(false);
    });

    it('should save', () => {
        componentFixture.componentRef.setInput('isEditing', true);
        componentFixture.detectChanges();
        component['form'].setValue(newValues);
        const saveSpy = vi.spyOn(component.onSave, 'emit');

        component.save();

        expect(saveSpy).toHaveBeenCalledWith({ ...defaultCompetency, ...newValues });
        expect(component.isEditing()).toBe(false);
    });

    it.each<[StandardizedCompetencyDTO, boolean]>([
        [defaultCompetency, false],
        [{ title: 'new competency' } as StandardizedCompetencyDTO, true],
    ])('should cancel and close', (competency, shouldClose) => {
        componentFixture.componentRef.setInput('competency', competency);
        componentFixture.componentRef.setInput('isEditing', true);
        componentFixture.detectChanges();
        component['form'].setValue(newValues);
        const closeSpy = vi.spyOn(component.onClose, 'emit');

        component.cancel();

        compareFormValues(component['form'].getRawValue(), competency);
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

    it('should update description', () => {
        componentFixture.detectChanges();
        component.updateDescriptionControl('new description');

        expect(component['form'].controls.description.getRawValue()).toBe('new description');
    });

    /**
     * Helper to compare form values with competency properties.
     */
    function compareFormValues(formValues: any, competency: StandardizedCompetencyDTO) {
        for (const key in formValues) {
            // Normalize null to undefined for comparison
            expect(formValues[key] ?? undefined).toEqual(competency[key as keyof StandardizedCompetencyDTO]);
        }
    }
});

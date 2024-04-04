import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { StandardizedCompetencyDetailComponent } from 'app/admin/standardized-competencies/standardized-competency-detail.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { KnowledgeAreaDTO, StandardizedCompetencyDTO } from 'app/entities/competency/standardized-competency.model';

describe('StandardizedCompetencyDetailComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyDetailComponent>;
    let component: StandardizedCompetencyDetailComponent;
    const defaultCompetency: StandardizedCompetencyDTO = {
        id: 1,
        title: 'title',
        description: 'description',
        taxonomy: CompetencyTaxonomy.ANALYZE,
        knowledgeAreaId: 1,
        sourceId: 1,
        version: '1.0.0',
    };
    const newValues = {
        title: 'new title',
        description: 'new description',
        taxonomy: CompetencyTaxonomy.APPLY,
        knowledgeAreaId: 2,
    };

    const defaultKnowledgeAreas: KnowledgeAreaDTO[] = [
        { id: 1, title: 'KA1' },
        { id: 2, title: 'KA2' },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule],
            declarations: [
                StandardizedCompetencyDetailComponent,
                MockComponent(ButtonComponent),
                TranslatePipeMock,
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(MarkdownEditorComponent),
                MockComponent(TaxonomySelectComponent),
                MockDirective(TranslateDirective),
                MockDirective(DeleteButtonDirective),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyDetailComponent);
                component = componentFixture.componentInstance;
                component.competency = defaultCompetency;
                component.knowledgeAreas = defaultKnowledgeAreas;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should set form values to competency', () => {
        componentFixture.detectChanges();
        compareFormValues(component.form.getRawValue(), defaultCompetency);
    });

    it('should disable/enable when setting edit mode', () => {
        component.isEditing = false;
        componentFixture.detectChanges();
        expect(component.form.disabled).toBeTrue();

        component.edit();
        expect(component.form.disabled).toBeFalse();
    });

    it('should save', () => {
        component.isEditing = true;
        component.form.setValue(newValues);
        const saveSpy = jest.spyOn(component.onSave, 'emit');

        component.save();

        expect(saveSpy).toHaveBeenCalledWith({ ...defaultCompetency, ...newValues });
        expect(component.isEditing).toBeFalse();
    });

    it.each<[StandardizedCompetencyDTO, boolean]>([
        [defaultCompetency, false],
        [{ title: 'new competency' } as StandardizedCompetencyDTO, true],
    ])('should cancel and close', (competency, shouldClose) => {
        component.competency = competency;
        component.isEditing = true;
        component.form.setValue(newValues);
        const closeSpy = jest.spyOn(component.onClose, 'emit');

        component.cancel();

        compareFormValues(component.form.getRawValue(), competency);
        expect(component.isEditing).toBeFalse();
        shouldClose ? expect(closeSpy).toHaveBeenCalled() : expect(closeSpy).not.toHaveBeenCalled();
    });

    it('should delete', () => {
        const deleteSpy = jest.spyOn(component.onDelete, 'emit');
        component.delete();

        expect(deleteSpy).toHaveBeenCalled();
    });

    it('should close', () => {
        const closeSpy = jest.spyOn(component.onClose, 'emit');
        component.close();

        expect(closeSpy).toHaveBeenCalled();
    });

    it('should update description', () => {
        component.updateDescriptionControl('new description');

        expect(component.form.controls.description.getRawValue()).toBe('new description');
    });

    function compareFormValues(formValues: any, competency: StandardizedCompetencyDTO) {
        for (const key in formValues) {
            //needed to ensure null becomes undefined
            expect(formValues[key] ?? undefined).toEqual(competency[key]);
        }
    }
});

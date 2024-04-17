import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { KnowledgeAreaDetailComponent } from 'app/admin/standardized-competencies/knowledge-area-detail.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';
import { TranslatePipeMock } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';
import { By } from '@angular/platform-browser';

describe('KnowledgeAreaDetailComponent', () => {
    let componentFixture: ComponentFixture<KnowledgeAreaDetailComponent>;
    let component: KnowledgeAreaDetailComponent;
    const defaultKnowledgeArea: KnowledgeAreaDTO = {
        id: 1,
        title: 'title',
        shortTitle: 'title',
        description: 'description',
        parentId: 1,
    };
    const newValues = {
        title: 'new title',
        shortTitle: 'new title',
        description: 'new description',
        parentId: 2,
    };

    const defaultKnowledgeAreas: KnowledgeAreaDTO[] = [
        { id: 1, title: 'KA1' },
        { id: 2, title: 'KA2' },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule],
            declarations: [
                KnowledgeAreaDetailComponent,
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
                componentFixture = TestBed.createComponent(KnowledgeAreaDetailComponent);
                component = componentFixture.componentInstance;
                component.knowledgeArea = defaultKnowledgeArea;
                component.knowledgeAreas = defaultKnowledgeAreas;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set form values to knowledgeArea', () => {
        componentFixture.detectChanges();
        compareFormValues(component.form.getRawValue(), defaultKnowledgeArea);
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

        expect(saveSpy).toHaveBeenCalledWith({ ...defaultKnowledgeArea, ...newValues });
        expect(component.isEditing).toBeFalse();
    });

    it.each<[KnowledgeAreaDTO, boolean]>([
        [defaultKnowledgeArea, false],
        [{ title: 'new knowledgeArea', shortTitle: 'new ka' } as KnowledgeAreaDTO, true],
    ])('should cancel and close', (knowledgeArea, shouldClose) => {
        component.knowledgeArea = knowledgeArea;
        component.isEditing = true;
        component.form.setValue(newValues);
        const closeSpy = jest.spyOn(component.onClose, 'emit');

        component.cancel();

        compareFormValues(component.form.getRawValue(), knowledgeArea);
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

    it('should open new knowledge area', () => {
        const openSpy = jest.spyOn(component.onOpenNewKnowledgeArea, 'emit');
        component.openNewKnowledgeArea();

        expect(openSpy).toHaveBeenCalled();
    });

    it('should open new competency', () => {
        const openSpy = jest.spyOn(component.onOpenNewCompetency, 'emit');
        component.openNewCompetency();

        expect(openSpy).toHaveBeenCalled();
    });

    it('should update description', () => {
        component.updateDescriptionControl('new description');

        expect(component.form.controls.description.getRawValue()).toBe('new description');
    });

    it.each<[number, boolean]>([
        [1, true],
        [2, true],
        [3, false],
    ])('should not allow circular dependencies', (newParentId, isInvalid) => {
        component.isEditing = true;
        //only the knowledge area 3 is ok because it is not a descendant of knowledge area 1
        component.knowledgeArea = {
            id: 1,
            title: 'KA1',
            shortTitle: 'title',
            children: [{ id: 2 }],
            parentId: 99,
        };
        component.knowledgeAreas = [{ id: 1 }, { id: 2 }, { id: 3 }];
        componentFixture.detectChanges();
        expect(component.form.controls.parentId.invalid).toBeFalse();

        const select = componentFixture.debugElement.query(By.css('#knowledge-area-select')).nativeElement;
        select.value = select.options[newParentId].value;
        select.dispatchEvent(new Event('change'));
        componentFixture.detectChanges();

        expect(component.form.controls.parentId.value).toEqual(newParentId);
        expect(component.form.controls.parentId.invalid).toEqual(isInvalid);
    });

    function compareFormValues(formValues: any, knowledgeArea: KnowledgeAreaDTO) {
        for (const key in formValues) {
            //needed to ensure null becomes undefined
            expect(formValues[key] ?? undefined).toEqual(knowledgeArea[key]);
        }
    }
});

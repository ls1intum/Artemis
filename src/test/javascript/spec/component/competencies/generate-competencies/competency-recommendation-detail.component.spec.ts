import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyRecommendationDetailComponent } from 'app/course/competencies/generate-competencies/competency-recommendation-detail.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { NgbTooltipMocksModule } from '../../../helpers/mocks/directive/ngbTooltipMocks.module';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { TaxonomySelectComponent } from 'app/course/competencies/taxonomy-select/taxonomy-select.component';

describe('CompetencyRecommendationDetailComponent', () => {
    let competencyRecommendationDetailComponentFixture: ComponentFixture<CompetencyRecommendationDetailComponent>;
    let competencyRecommendationDetailComponent: CompetencyRecommendationDetailComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ReactiveFormsModule, NgbTooltipMocksModule, NgbCollapseMocksModule],
            declarations: [
                CompetencyRecommendationDetailComponent,
                ButtonComponent,
                MockDirective(FeatureToggleDirective),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(MarkdownEditorComponent),
                MockComponent(TaxonomySelectComponent),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                competencyRecommendationDetailComponentFixture = TestBed.createComponent(CompetencyRecommendationDetailComponent);
                competencyRecommendationDetailComponent = competencyRecommendationDetailComponentFixture.componentInstance;
            });
    });

    beforeEach(() => {
        //initialize component
        competencyRecommendationDetailComponent.form = new FormGroup({
            competency: new FormGroup({
                title: new FormControl('Title' as string | undefined, { nonNullable: true }),
                description: new FormControl('Description' as string | undefined, { nonNullable: true }),
                taxonomy: new FormControl(CompetencyTaxonomy.ANALYZE as CompetencyTaxonomy | undefined, { nonNullable: true }),
            }),
            viewed: new FormControl(false, { nonNullable: true }),
        });
        competencyRecommendationDetailComponent.index = 0;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        competencyRecommendationDetailComponentFixture.detectChanges();
        expect(competencyRecommendationDetailComponent).toBeDefined();
    });

    it('should switch between edit and save mode', () => {
        competencyRecommendationDetailComponentFixture.detectChanges();
        const editSpy = jest.spyOn(competencyRecommendationDetailComponent, 'edit');
        const saveSpy = jest.spyOn(competencyRecommendationDetailComponent, 'save');

        //component should not start out in edit mode
        expect(competencyRecommendationDetailComponent.isInEditMode).toBeFalse();
        expect(competencyRecommendationDetailComponent.form.controls.competency.disabled).toBeTrue();

        const editButton = competencyRecommendationDetailComponentFixture.debugElement.nativeElement.querySelector('#editButton-0 > .jhi-btn');
        editButton.click();

        expect(editSpy).toHaveBeenCalledOnce();
        competencyRecommendationDetailComponentFixture.detectChanges();

        const saveButton = competencyRecommendationDetailComponentFixture.debugElement.nativeElement.querySelector('#saveButton-0 > .jhi-btn');
        saveButton.click();

        expect(saveSpy).toHaveBeenCalledOnce();
    });

    it('should delete', () => {
        competencyRecommendationDetailComponentFixture.detectChanges();
        const deleteSpy = jest.spyOn(competencyRecommendationDetailComponent, 'delete');
        const deleteButton = competencyRecommendationDetailComponentFixture.debugElement.nativeElement.querySelector('#deleteButton-0 > .jhi-btn');

        deleteButton.click();

        expect(deleteSpy).toHaveBeenCalledOnce();
    });

    it('should expand', () => {
        competencyRecommendationDetailComponentFixture.detectChanges();
        const toggleSpy = jest.spyOn(competencyRecommendationDetailComponent, 'toggle');
        const expandIcon = competencyRecommendationDetailComponentFixture.debugElement.nativeElement.querySelector('.rotate-icon');

        expandIcon.click();

        expect(toggleSpy).toHaveBeenCalledOnce();
    });
});

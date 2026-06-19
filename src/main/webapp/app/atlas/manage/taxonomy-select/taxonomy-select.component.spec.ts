import { vi } from 'vitest';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MockDirective } from 'ng-mocks';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { By } from '@angular/platform-browser';
import { Select } from 'primeng/select';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('TaxonomySelectComponent', () => {
    setupTestBed({ zoneless: true });
    let componentFixture: ComponentFixture<TaxonomySelectComponent>;
    let component: TaxonomySelectComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, TaxonomySelectComponent, MockDirective(TranslateDirective)],
            declarations: [],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        componentFixture = TestBed.createComponent(TaxonomySelectComponent);
        component = componentFixture.componentInstance;
        componentFixture.componentRef.setInput('form', new FormControl<CompetencyTaxonomy | undefined>(undefined));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should set taxonomy correcty', () => {
        componentFixture.detectChanges();

        // The PrimeNG p-select must be rendered and initially hold no selected option.
        const select = componentFixture.debugElement.query(By.directive(Select));
        expect(select).not.toBeNull();
        const selectInstance: Select = select.componentInstance;
        // No taxonomy selected yet (the p-select ControlValueAccessor normalizes the empty value to null).
        expect((component.form() as FormControl).value).toBeFalsy();
        expect(selectInstance.selectedOption).toBeFalsy();

        // Selecting a taxonomy via the bound form control sets the value...
        (component.form() as FormControl).setValue(CompetencyTaxonomy.ANALYZE);
        componentFixture.detectChanges();

        // ...which is reflected both in the form control and in the p-select's selected option.
        expect((component.form() as FormControl).value).toBe(CompetencyTaxonomy.ANALYZE);
        expect(selectInstance.selectedOption?.value).toBe(CompetencyTaxonomy.ANALYZE);
    });
});

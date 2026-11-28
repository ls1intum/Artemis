import { vi } from 'vitest';
import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { By } from '@angular/platform-browser';
import { TumAetUiSelectComponent } from '@tumaet/ui-angular/select';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TaxonomySelectComponent', () => {
    let componentFixture: ComponentFixture<TaxonomySelectComponent>;
    let component: TaxonomySelectComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, TaxonomySelectComponent],
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

    it('should offer one option per taxonomy', () => {
        componentFixture.detectChanges();

        const select = componentFixture.debugElement.query(By.directive(TumAetUiSelectComponent)).componentInstance as TumAetUiSelectComponent;
        expect(select.options().map((option) => (option as { value: CompetencyTaxonomy }).value)).toEqual(Object.values(CompetencyTaxonomy));
    });

    it('should set taxonomy correctly', () => {
        componentFixture.detectChanges();

        const select = componentFixture.debugElement.query(By.directive(TumAetUiSelectComponent));
        expect(select).not.toBeNull();
        // No taxonomy selected yet (the select's ControlValueAccessor normalizes the empty value to null).
        expect((component.form() as FormControl).value).toBeNull();

        (component.form() as FormControl).setValue(CompetencyTaxonomy.ANALYZE);
        componentFixture.detectChanges();

        expect((component.form() as FormControl).value).toBe(CompetencyTaxonomy.ANALYZE);
    });
});

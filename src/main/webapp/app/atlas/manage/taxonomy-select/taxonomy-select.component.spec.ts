import { TaxonomySelectComponent } from 'app/atlas/manage/taxonomy-select/taxonomy-select.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MockDirective } from 'ng-mocks';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { By } from '@angular/platform-browser';
import { TranslateDirective } from 'app/shared/language/translate.directive';

describe('TaxonomySelectComponent', () => {
    let componentFixture: ComponentFixture<TaxonomySelectComponent>;
    let component: TaxonomySelectComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule],
            declarations: [TaxonomySelectComponent, MockDirective(TranslateDirective)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(TaxonomySelectComponent);
                component = componentFixture.componentInstance;
                componentFixture.componentRef.setInput('form', new FormControl<CompetencyTaxonomy | undefined>(undefined));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should set taxonomy correcty', () => {
        const select = componentFixture.debugElement.query(By.css('.form-select')).nativeElement;
        expect(select.value).toBe('');

        (component.form() as FormControl).setValue(CompetencyTaxonomy.ANALYZE);
        componentFixture.detectChanges();

        expect(select.value).toContain(CompetencyTaxonomy.ANALYZE);
    });
});

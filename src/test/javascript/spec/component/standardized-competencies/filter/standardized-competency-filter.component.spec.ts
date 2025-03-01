import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { MockDirective } from 'ng-mocks';

describe('StandardizedCompetencyFilterComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyFilterComponent>;
    let component: StandardizedCompetencyFilterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [StandardizedCompetencyFilterComponent, FormsModule, MockDirective(TranslateDirective)],
            declarations: [],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyFilterComponent);
                component = componentFixture.componentInstance;
            });
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });
});

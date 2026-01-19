import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { StandardizedCompetencyFilterComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter.component';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('StandardizedCompetencyFilterComponent', () => {
    setupTestBed({ zoneless: true });
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

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { ArtemisTestModule } from '../../../test.module';

describe('StandardizedCompetencyFilterComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyFilterComponent>;
    let component: StandardizedCompetencyFilterComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [StandardizedCompetencyFilterComponent, ArtemisTestModule, FormsModule],
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

import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ImportCompetenciesComponent } from 'app/course/competencies/import-competencies/import-competencies.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';
import { CompetencyTableComponent } from 'app/course/competencies/import-competencies/competency-table.component';
import { FormsModule } from 'app/forms/forms.module';

describe('ImportCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesComponent>;
    let component: ImportCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ImportCompetenciesComponent,
                MockPipe(ArtemisTranslatePipe),
                ButtonComponent,
                MockComponent(CompetencySearchComponent),
                MockComponent(CompetencyTableComponent),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportCompetenciesComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });
});

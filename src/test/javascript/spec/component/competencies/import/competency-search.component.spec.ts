import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';
import { NgbCollapseMocksModule } from '../../../helpers/mocks/directive/ngbCollapseMocks.module';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';

describe('CompetencySearchComponent', () => {
    let componentFixture: ComponentFixture<CompetencySearchComponent>;
    let component: CompetencySearchComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [CompetencySearchComponent, MockPipe(ArtemisTranslatePipe), NgbCollapseMocksModule, ButtonComponent],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CompetencySearchComponent);
                component = componentFixture.componentInstance;
                component.search = {
                    title: '',
                    semester: '',
                    courseTitle: '',
                    description: '',
                };
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

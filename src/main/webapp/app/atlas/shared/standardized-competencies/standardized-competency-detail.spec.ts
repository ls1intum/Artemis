import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';
import { MockDirective, MockPipe } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';

describe('StandardizedCompetencyDetailComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyDetailComponent>;
    let component: StandardizedCompetencyDetailComponent;
    const defaultCompetency: StandardizedCompetencyDTO = {
        id: 1,
        title: 'title',
        description: 'description',
        taxonomy: CompetencyTaxonomy.ANALYZE,
        knowledgeAreaId: 1,
        sourceId: 1,
        version: '1.0.0',
    };
    const defaultKnowledgeAreaTitle = 'knowledgeArea';

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [StandardizedCompetencyDetailComponent],
            declarations: [MockPipe(HtmlForMarkdownPipe), MockDirective(TranslateDirective)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyDetailComponent);
                component = componentFixture.componentInstance;
                componentFixture.componentRef.setInput('competency', defaultCompetency);
                componentFixture.componentRef.setInput('knowledgeAreaTitle', defaultKnowledgeAreaTitle);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should close', () => {
        const closeSpy = jest.spyOn(component.onClose, 'emit');
        component.close();

        expect(closeSpy).toHaveBeenCalled();
    });
});

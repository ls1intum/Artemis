import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { MatTreeModule } from '@angular/material/tree';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

describe('KnowledgeAreaTreeComponent', () => {
    let componentFixture: ComponentFixture<KnowledgeAreaTreeComponent>;
    let component: KnowledgeAreaTreeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [KnowledgeAreaTreeComponent, ArtemisTestModule, ArtemisSharedCommonModule, MatTreeModule, ArtemisMarkdownModule],
            declarations: [MockPipe(ArtemisTranslatePipe)],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(KnowledgeAreaTreeComponent);
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

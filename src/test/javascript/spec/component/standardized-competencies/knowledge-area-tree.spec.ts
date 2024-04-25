import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe } from 'ng-mocks';
import { KnowledgeAreaTreeComponent } from 'app/admin/standardized-competencies/knowledge-area-tree/knowledge-area-tree.component';
import { NgbCollapseMocksModule } from '../../helpers/mocks/directive/ngbCollapseMocks.module';
import { MatTreeModule } from '@angular/material/tree';

describe('KnowledgeAreaTreeComponent', () => {
    let componentFixture: ComponentFixture<KnowledgeAreaTreeComponent>;
    let component: KnowledgeAreaTreeComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbCollapseMocksModule, MatTreeModule],
            declarations: [KnowledgeAreaTreeComponent, MockPipe(ArtemisTranslatePipe)],
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

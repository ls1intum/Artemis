import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { AdminImportStandardizedCompetenciesComponent } from 'app/admin/standardized-competencies/import/admin-import-standardized-competencies.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { KnowledgeAreaTreeStubComponent } from './knowledge-area-tree-stub.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { NgbCollapseMocksModule } from '../../helpers/mocks/directive/ngbCollapseMocks.module';

describe('ImportStandardizedCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<AdminImportStandardizedCompetenciesComponent>;
    let component: AdminImportStandardizedCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, NgbCollapseMocksModule],
            declarations: [
                //TODO: mock router etc
                AdminImportStandardizedCompetenciesComponent,
                MockPipe(HtmlForMarkdownPipe),
                KnowledgeAreaTreeStubComponent,
                MockComponent(ButtonComponent),
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(AdminImportStandardizedCompetenciesComponent);
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

    //mock incorrect files?

    it('should cancel', () => {
        //TODO: press cancel and go back.
    });

    it('should toggle collapse', () => {
        //TODO: press cancel and go back.
    });
});

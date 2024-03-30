import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MockComponent, MockProvider } from 'ng-mocks';
import { StandardizedCompetencyManagementComponent } from 'app/admin/standardized-competencies/standardized-competency-management.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MatTreeModule } from '@angular/material/tree';
import { StandardizedCompetencyDetailStubComponent } from './standardized-competency-detail-stub';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import { KnowledgeAreaDTO } from 'app/entities/competency/standardized-competency.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { NgbCollapseMocksModule } from '../../helpers/mocks/directive/ngbCollapseMocks.module';

describe('StandardizedCompetencyManagementComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyManagementComponent>;
    let component: StandardizedCompetencyManagementComponent;
    let competencyService: StandardizedCompetencyService;
    let getForTreeViewSpy: jest.SpyInstance;
    let defaultTree: KnowledgeAreaDTO[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MatTreeModule, FormsModule, NgbTooltipMocksModule, NgbCollapseMocksModule],
            declarations: [StandardizedCompetencyManagementComponent, StandardizedCompetencyDetailStubComponent, MockComponent(ButtonComponent), MockRouterLinkDirective],
            providers: [ArtemisTranslatePipe, MockProvider(StandardizedCompetencyService), MockProvider(AdminStandardizedCompetencyService)],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyManagementComponent);
                component = componentFixture.componentInstance;
                competencyService = TestBed.inject(StandardizedCompetencyService);

                defaultTree = [
                    {
                        id: 1,
                        children: [{ id: 11 }, { id: 12 }],
                    },
                    {
                        id: 2,
                    },
                ];
                getForTreeViewSpy = jest.spyOn(competencyService, 'getAllForTreeView').mockReturnValue(of(new HttpResponse({ body: defaultTree })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    /* Comment this out so eslint is happy :) TODO: use this for the test later
    function createKnowledgeAreaDTO(id?: number, title?: string, description?: string, parentId?: number, children?: KnowledgeAreaDTO[], competencies?: StandardizedCompetencyDTO[]) {
        const knowledgeArea: KnowledgeAreaDTO = {
            id: id,
            title: title,
            description: description,
            parentId: parentId,
            children: children,
            competencies: competencies
        }
        return knowledgeArea
    }

    function createCompetencyDTO(id?: number, title?: string, description?: string, taxonomy?: CompetencyTaxonomy, version?: string, sourceId?: number, knowledgeAreaId?: number) {
        const competency: StandardizedCompetencyDTO = {
            id: id,
            title: title,
            description: description,
            taxonomy: taxonomy,
            version: version,
            sourceId: sourceId,
            knowledgeAreaId: knowledgeAreaId
        }
    }*/

    it('should load data on init', () => {
        componentFixture.detectChanges();

        expect(getForTreeViewSpy).toHaveBeenCalled();
        expect(component['knowledgeAreaMap'].size).toBe(4);
        expect(component['knowledgeAreasForSelect']).toHaveLength(4);
        expect(component['dataSource'].data).toHaveLength(2);
    });

    it('should filter by knowledge area', () => {});

    it('should filter by title', () => {});

    it('should open new competency', () => {});

    it('should select competency', () => {});

    //TODO: callbacks here

    it('should close competency', () => {});

    it('should delete competency', () => {});

    it('should create competency', () => {});

    it('should update competency', () => {});
});

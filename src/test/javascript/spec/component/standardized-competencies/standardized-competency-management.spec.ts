import { ArtemisTestModule } from '../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { MockComponent, MockProvider } from 'ng-mocks';
import { StandardizedCompetencyManagementComponent } from 'app/admin/standardized-competencies/standardized-competency-management.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { StandardizedCompetencyDetailStubComponent } from './standardized-competency-detail-stub';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { AdminStandardizedCompetencyService } from 'app/admin/standardized-competencies/admin-standardized-competency.service';
import {
    KnowledgeAreaDTO,
    KnowledgeAreaForTree,
    StandardizedCompetencyDTO,
    StandardizedCompetencyForTree,
    convertToKnowledgeAreaForTree,
} from 'app/entities/competency/standardized-competency.model';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { NgbTooltipMocksModule } from '../../helpers/mocks/directive/ngbTooltipMocks.module';
import { NgbCollapseMocksModule } from '../../helpers/mocks/directive/ngbCollapseMocks.module';
import { By } from '@angular/platform-browser';
import { CompetencyTaxonomy } from 'app/entities/competency.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { KnowledgeAreaDetailStubComponent } from './knowledge-area-detail-stub.component';
import { KnowledgeAreaTreeStubComponent } from './knowledge-area-tree-stub.component';

describe('StandardizedCompetencyManagementComponent', () => {
    let componentFixture: ComponentFixture<StandardizedCompetencyManagementComponent>;
    let component: StandardizedCompetencyManagementComponent;
    let competencyService: StandardizedCompetencyService;
    let getForTreeViewSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateTestingModule, ArtemisTestModule, FormsModule, NgbTooltipMocksModule, NgbCollapseMocksModule],
            declarations: [
                StandardizedCompetencyManagementComponent,
                StandardizedCompetencyDetailStubComponent,
                KnowledgeAreaDetailStubComponent,
                MockComponent(ButtonComponent),
                KnowledgeAreaTreeStubComponent,
                MockRouterLinkDirective,
            ],
            providers: [ArtemisTranslatePipe, MockProvider(StandardizedCompetencyService), MockProvider(AdminStandardizedCompetencyService), MockProvider(NgbModal)],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(StandardizedCompetencyManagementComponent);
                component = componentFixture.componentInstance;
                competencyService = TestBed.inject(StandardizedCompetencyService);
                getForTreeViewSpy = jest.spyOn(competencyService, 'getAllForTreeView').mockReturnValue(of(new HttpResponse({ body: [{ id: 1 }] })));
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should load data on init', () => {
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                children: [
                    {
                        id: 11,
                        parentId: 1,
                        children: [{ id: 111, parentId: 11 }],
                    },
                    { id: 12, parentId: 1 },
                ],
            },
            {
                id: 2,
                children: [{ id: 21, parentId: 2 }],
            },
            {
                id: 3,
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        componentFixture.detectChanges();

        expect(getForTreeViewSpy).toHaveBeenCalled();
        expect(component['knowledgeAreaMap'].size).toBe(7);
        expect(component['knowledgeAreasForSelect']).toHaveLength(7);
        expect(component['dataSource'].data).toHaveLength(3);
    });

    it('should filter by knowledge area', () => {
        const filterTree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                children: [
                    {
                        id: 11,
                        title: 'knowledge area to filter by',
                        parentId: 1,
                        children: [{ id: 111, parentId: 11 }],
                    },
                    { id: 12, parentId: 1 },
                ],
            },
            {
                id: 2,
                children: [{ id: 21, parentId: 2 }],
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: filterTree })));
        const filterSpy = jest.spyOn(component, 'filterByKnowledgeArea');
        componentFixture.detectChanges();

        const select: HTMLSelectElement = componentFixture.debugElement.query(By.css('#knowledge-area-filter')).nativeElement;
        //knowledge area 11 should be the third element (undefined -> 1 -> 11 -> ...)
        select.value = select.options[2].value;
        select.dispatchEvent(new Event('change'));
        componentFixture.detectChanges();

        expect(component['knowledgeAreaMap'].size).toBe(6);
        expect(filterSpy).toHaveBeenCalledOnce();
        const validIds = [1, 11, 111];
        for (const knowledgeArea of component['knowledgeAreaMap'].values()) {
            if (validIds.includes(knowledgeArea.id!)) {
                expect(knowledgeArea.isVisible).toBeTrue();
            } else {
                expect(knowledgeArea.isVisible).toBeFalse();
            }
        }

        //test that the filter resets again
        select.value = 'undefined';
        select.dispatchEvent(new Event('change'));
        componentFixture.detectChanges();

        for (const knowledgeArea of component['knowledgeAreaMap'].values()) {
            expect(knowledgeArea.isVisible).toBeTrue();
        }
    });

    it('should filter by title', fakeAsync(() => {
        const filter = '   FiLter  ';
        const validIds = [1, 2, 3, 4];
        //filter matches
        const c1 = createCompetencyDTO(1, 'Filter Match1');
        const c2 = createCompetencyDTO(2, 'fIlTeR match2');
        const c3 = createCompetencyDTO(3, 'a long text filter match');
        const c4 = createCompetencyDTO(4, 'filter');
        //no filter matches
        const c11 = createCompetencyDTO(11, 'filte no match');
        const c12 = createCompetencyDTO(12, 'filteXr no match');
        const c13 = createCompetencyDTO(13, 'filte');
        const c14 = createCompetencyDTO(14, '');
        const c15 = createCompetencyDTO(15, undefined);
        const filterTree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [c1, c2, c3, c4, c11, c12, c13, c14, c15],
                children: [{ id: 2 }],
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: filterTree })));
        const filterSpy = jest.spyOn(component, 'filterByCompetencyTitle');
        componentFixture.detectChanges();

        const input: HTMLSelectElement = componentFixture.debugElement.query(By.css('#title-filter')).nativeElement;
        input.value = filter;
        input.dispatchEvent(new Event('input'));
        componentFixture.detectChanges();
        //wait until the debounce is done
        tick(600);

        expect(component['knowledgeAreaMap'].size).toBe(2);
        expect(filterSpy).toHaveBeenCalledOnce();
        const knowledgeArea = component['knowledgeAreaMap'].get(1)!;
        for (const competency of knowledgeArea.competencies!) {
            if (validIds.includes(competency.id!)) {
                expect(competency.isVisible).toBeTrue();
            } else {
                expect(competency.isVisible).toBeFalse();
            }
        }

        //test that the filter resets again
        input.value = '   ';
        input.dispatchEvent(new Event('input'));
        componentFixture.detectChanges();
        //wait until the debounce is done
        tick(600);

        for (const competency of knowledgeArea.competencies!) {
            expect(competency.isVisible).toBeTrue();
        }
    }));

    it('should open cancel modal', () => {
        const modalRef = {
            result: Promise.resolve(),
            componentInstance: {},
        } as NgbModalRef;
        const modalService = TestBed.inject(NgbModal);
        const openSpy = jest.spyOn(modalService, 'open').mockReturnValue(modalRef);

        component['openCancelModal']('title', 'standardizedCompetency', () => {});

        expect(openSpy).toHaveBeenCalledOnce();
    });

    it('should open new competency', () => {
        const knowledgeArea: KnowledgeAreaDTO = { id: 1 };
        const knowledgeArea2: KnowledgeAreaDTO = { id: 2 };
        const expectedCompetency: StandardizedCompetencyDTO = { knowledgeAreaId: 1 };
        const expectedCompetency2: StandardizedCompetencyDTO = { knowledgeAreaId: 2 };
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());

        component['isEditing'] = false;
        component.openNewCompetency(knowledgeArea.id!);

        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedCompetency']).toEqual(expectedCompetency);
        expect(component['isEditing']).toBeTrue();

        component.openNewCompetency(knowledgeArea2.id!);
        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedCompetency']).toEqual(expectedCompetency2);
    });

    it('should select competency', () => {
        const expectedCompetency = createCompetencyDTO(1, 'title1', 'description1');
        const expectedCompetency2 = createCompetencyDTO(2, 'title2', 'description2');
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());

        component['isEditing'] = false;
        component.selectCompetency(expectedCompetency);

        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedCompetency']).toEqual(expectedCompetency);

        //nothing should happen if the same competency is selected twice
        component['isEditing'] = true;
        component.selectCompetency(expectedCompetency);
        expect(cancelModalSpy).not.toHaveBeenCalled();

        component.selectCompetency(expectedCompetency2);
        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedCompetency']).toEqual(expectedCompetency2);
    });

    it('should close competency', () => {
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());
        const expectedCompetency = createCompetencyDTO(1, 'title1', 'description1');
        component['selectedCompetency'] = expectedCompetency;
        component['isEditing'] = false;

        component.closeCompetency();

        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedCompetency']).toBeUndefined();

        component['selectedCompetency'] = expectedCompetency;
        component['isEditing'] = true;

        component.closeCompetency();

        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedCompetency']).toBeUndefined();
    });

    it('should delete competency', () => {
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const deleteSpy = jest.spyOn(adminStandardizedCompetencyService, 'deleteStandardizedCompetency').mockReturnValue(of(new HttpResponse<void>()));
        const competencyToDelete: StandardizedCompetencyDTO = { id: 1, title: 'competency1', knowledgeAreaId: 1 };
        const c2: StandardizedCompetencyForTree = { id: 2, title: 'competency2', knowledgeAreaId: 1, isVisible: true };
        const c3: StandardizedCompetencyForTree = { id: 3, title: 'competency3', knowledgeAreaId: 1, isVisible: true };
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [competencyToDelete, c2, c3],
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        component['selectedCompetency'] = competencyToDelete;
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(StandardizedCompetencyDetailStubComponent)).componentInstance;
        detailComponent.onDelete.emit(competencyToDelete.id);

        expect(deleteSpy).toHaveBeenCalledOnce();
        const competencies = component['knowledgeAreaMap'].get(1)!.competencies!;
        expect(competencies).toContainAllValues([c2, c3]);
        expect(competencies).toHaveLength(2);
    });

    it('should create competency', () => {
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [{ id: 1 }, { id: 2 }],
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));

        //important that this has no id, so we create a competency!
        const competencyToCreate: StandardizedCompetencyDTO = { title: 'competency1', knowledgeAreaId: 1 };
        const createdCompetency: StandardizedCompetencyDTO = { id: 5, ...competencyToCreate };
        const expectedCompetencyInTree: StandardizedCompetencyForTree = { ...createdCompetency, isVisible: true };
        component['selectedCompetency'] = competencyToCreate;
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const createSpy = jest.spyOn(adminStandardizedCompetencyService, 'createStandardizedCompetency');
        createSpy.mockReturnValue(of(new HttpResponse({ body: createdCompetency })));
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(StandardizedCompetencyDetailStubComponent)).componentInstance;
        detailComponent.onSave.emit(competencyToCreate);

        expect(createSpy).toHaveBeenCalled();
        const competencies = component['knowledgeAreaMap'].get(1)!.competencies!;
        expect(competencies).toHaveLength(3);
        expect(competencies).toContainEqual(expectedCompetencyInTree);
    });

    it('should update competency', () => {
        const competencyToUpdate = createCompetencyDTO(1, 'title', 'description', CompetencyTaxonomy.ANALYZE, 1);
        const updatedCompetency = createCompetencyDTO(1, 'new title', 'new description', CompetencyTaxonomy.CREATE, 1);
        const expectedCompetencyInTree: StandardizedCompetencyForTree = { ...updatedCompetency, isVisible: true };
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [competencyToUpdate, { id: 2 }],
            },
        ];

        prepareAndExecuteCompetencyUpdate(tree, competencyToUpdate, updatedCompetency);

        const competencies = component['knowledgeAreaMap'].get(1)!.competencies!;
        expect(competencies).toContainEqual(expectedCompetencyInTree);
        expect(competencies).toHaveLength(2);
    });

    it('should move competency on update', () => {
        const competencyToUpdate = createCompetencyDTO(1, 'title', 'description', CompetencyTaxonomy.ANALYZE, 1);
        const updatedCompetency = createCompetencyDTO(1, 'new title', 'new description', CompetencyTaxonomy.ANALYZE, 2);
        const expectedCompetencyInTree: StandardizedCompetencyForTree = { ...updatedCompetency, isVisible: true };
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                competencies: [{ id: 1 }, { id: 2 }],
            },
            {
                id: 2,
                competencies: [{ id: 21, title: 'competency 21' }],
            },
        ];

        prepareAndExecuteCompetencyUpdate(tree, competencyToUpdate, updatedCompetency);

        const competencies1 = component['knowledgeAreaMap'].get(1)!.competencies!;
        expect(competencies1).not.toContainEqual(expectedCompetencyInTree);
        expect(competencies1).toHaveLength(1);
        const competencies2 = component['knowledgeAreaMap'].get(2)!.competencies!;
        expect(competencies2).toContainEqual(expectedCompetencyInTree);
        expect(competencies2).toHaveLength(2);
    });

    it('should open new knowledgeArea', () => {
        const expectedKnowledgeArea1: KnowledgeAreaDTO = { parentId: 1 };
        const expectedKnowledgeArea2: KnowledgeAreaDTO = { parentId: 2 };
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());

        component['isEditing'] = false;
        component.openNewKnowledgeArea(expectedKnowledgeArea1.parentId);

        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toEqual(expectedKnowledgeArea1);
        expect(component['isEditing']).toBeTrue();

        component.openNewKnowledgeArea(expectedKnowledgeArea2.parentId);
        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toEqual(expectedKnowledgeArea2);
    });

    it('should select knowledgeArea', () => {
        const expectedKnowledgeArea = createKnowledgeAreaDTO(1, 'title1', 't1');
        const expectedKnowledgeArea2 = createKnowledgeAreaDTO(2, 'title2', 't2');
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());

        component['isEditing'] = false;
        component.selectKnowledgeArea(expectedKnowledgeArea);
        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toEqual(expectedKnowledgeArea);

        //nothing should happen if the same competency is selected twice
        component['isEditing'] = true;
        component.selectKnowledgeArea(expectedKnowledgeArea);
        expect(cancelModalSpy).not.toHaveBeenCalled();

        component.selectKnowledgeArea(expectedKnowledgeArea2);
        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toEqual(expectedKnowledgeArea2);
    });

    it('should close knowledgeArea', () => {
        const cancelModalSpy = jest.spyOn(component as any, 'openCancelModal').mockImplementation((title, entityType, callback: () => void) => callback());
        const expectedKnowledgeArea = createKnowledgeAreaDTO(1, 'title1', 't1');
        component['selectedKnowledgeArea'] = expectedKnowledgeArea;
        component['isEditing'] = false;

        component.closeKnowledgeArea();

        expect(cancelModalSpy).not.toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toBeUndefined();

        component['selectedKnowledgeArea'] = expectedKnowledgeArea;
        component['isEditing'] = true;

        component.closeKnowledgeArea();

        expect(cancelModalSpy).toHaveBeenCalled();
        expect(component['selectedKnowledgeArea']).toBeUndefined();
    });

    it('should delete knowledgeArea', () => {
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const deleteSpy = jest.spyOn(adminStandardizedCompetencyService, 'deleteKnowledgeArea').mockReturnValue(of(new HttpResponse<void>()));
        const knowledgeAreaToDelete = createKnowledgeAreaDTO(1, 'title', 't1', 'd1');
        const ka2: KnowledgeAreaForTree = { id: 2, title: 'title2', isVisible: true, level: 0 };
        const ka3: KnowledgeAreaForTree = { id: 3, title: 'title2', isVisible: true, level: 0 };
        const tree: KnowledgeAreaDTO[] = [knowledgeAreaToDelete, ka2, ka3];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        component['selectedKnowledgeArea'] = knowledgeAreaToDelete;
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(KnowledgeAreaDetailStubComponent)).componentInstance;
        detailComponent.onDelete.emit(knowledgeAreaToDelete.id);

        expect(deleteSpy).toHaveBeenCalledOnce();
        const knowledgeAreas = Array.from(component['knowledgeAreaMap'].values());
        expect(knowledgeAreas).toContainAllValues([ka2, ka3]);
        expect(knowledgeAreas).toHaveLength(2);
    });

    it('should delete knowledgeArea and descendants', () => {
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const deleteSpy = jest.spyOn(adminStandardizedCompetencyService, 'deleteKnowledgeArea').mockReturnValue(of(new HttpResponse<void>()));

        const deletedIds = [10, 11, 12, 13];
        const childToDelete2_2 = createKnowledgeAreaDTO(deletedIds[3], 'titleA', 'tA', '', deletedIds[2]);
        const childToDelete2 = createKnowledgeAreaDTO(deletedIds[2], 'titleB', 'tB', '', deletedIds[0], [childToDelete2_2]);
        const childToDelete1 = createKnowledgeAreaDTO(deletedIds[1], 'titleC', 'tC', '', deletedIds[0]);
        const knowledgeAreaToDelete = createKnowledgeAreaDTO(deletedIds[0], 'kaToDelete', 'kTD', '', 1, [childToDelete1, childToDelete2]);

        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                title: 'ka1',
                children: [knowledgeAreaToDelete],
            },
            {
                id: 2,
                title: 'ka2',
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        component['selectedKnowledgeArea'] = knowledgeAreaToDelete;
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(KnowledgeAreaDetailStubComponent)).componentInstance;
        detailComponent.onDelete.emit(knowledgeAreaToDelete.id);

        expect(deleteSpy).toHaveBeenCalledOnce();
        const knowledgeAreaMap = component['knowledgeAreaMap'];
        for (const id of deletedIds) {
            const deletedKnowledgeArea = knowledgeAreaMap.get(id);
            expect(deletedKnowledgeArea).toBeUndefined();
        }
        expect(knowledgeAreaMap.get(1)).toBeDefined();
        expect(knowledgeAreaMap.get(2)).toBeDefined();
    });

    it('should create knowledgeArea', () => {
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));

        //important that this has no id, so we create!
        const knowledgeAreaToCreate: KnowledgeAreaDTO = { title: 'ka1', parentId: 1 };
        const createdKnowledgeArea: KnowledgeAreaDTO = { id: 5, ...knowledgeAreaToCreate };
        const expectedKnowledgeAreaInTree = convertToKnowledgeAreaForTree(createdKnowledgeArea, true, 1);
        component['selectedKnowledgeArea'] = knowledgeAreaToCreate;
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const createSpy = jest.spyOn(adminStandardizedCompetencyService, 'createKnowledgeArea');
        createSpy.mockReturnValue(of(new HttpResponse({ body: createdKnowledgeArea })));
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(KnowledgeAreaDetailStubComponent)).componentInstance;
        detailComponent.onSave.emit(knowledgeAreaToCreate);

        expect(createSpy).toHaveBeenCalled();
        const knowledgeAreaMap = component['knowledgeAreaMap'];
        expect(knowledgeAreaMap.size).toBe(2);
        expect(knowledgeAreaMap.get(5)).toEqual(expectedKnowledgeAreaInTree);
    });

    it('should hide created knowledgeArea if it is not in the filter', () => {
        component['knowledgeAreaFilter'] = { id: 2 };
        const tree: KnowledgeAreaDTO[] = [
            {
                id: 1,
                title: 'title',
                children: [{ id: 2, title: 'ka2' }],
            },
        ];
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));

        //important that this has no id, so we create!
        const knowledgeAreaToCreate: KnowledgeAreaDTO = { title: 'ka1', parentId: 1 };
        const createdKnowledgeArea: KnowledgeAreaDTO = { id: 5, ...knowledgeAreaToCreate };
        const expectedKnowledgeAreaInTree = convertToKnowledgeAreaForTree(createdKnowledgeArea, false, 1);
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const createSpy = jest.spyOn(adminStandardizedCompetencyService, 'createKnowledgeArea');
        createSpy.mockReturnValue(of(new HttpResponse({ body: createdKnowledgeArea })));
        componentFixture.detectChanges();

        component.saveKnowledgeArea(knowledgeAreaToCreate);

        expect(createSpy).toHaveBeenCalled();
        const knowledgeAreaMap = component['knowledgeAreaMap'];
        expect(knowledgeAreaMap.size).toBe(3);
        expect(knowledgeAreaMap.get(5)).toEqual(expectedKnowledgeAreaInTree);
    });

    it('should update knowledgeArea', () => {
        const knowledgeAreaToUpdate = createKnowledgeAreaDTO(1, 'long title', 'title', 'description');
        const updatedKnowledgeArea = createKnowledgeAreaDTO(1, 'new long title', 'new title', 'new description');
        const expectedKnowledgeAreaInTree = convertToKnowledgeAreaForTree(updatedKnowledgeArea, true, 0);
        const tree: KnowledgeAreaDTO[] = [knowledgeAreaToUpdate, { id: 2, title: 'another title' }];

        prepareAndExecuteKnowledgeAreaUpdate(tree, knowledgeAreaToUpdate, updatedKnowledgeArea);

        const knowledgeAreaMap = component['knowledgeAreaMap'];
        expect(knowledgeAreaMap.get(1)).toEqual(expectedKnowledgeAreaInTree);
        expect(knowledgeAreaMap.size).toBe(2);
    });

    it('should move knowledgeArea on update', () => {
        const parentId = 1;
        const newParentId = 2;
        const knowledgeAreaToUpdate = createKnowledgeAreaDTO(10, 'long title', 'title', 'description', parentId);
        const updatedKnowledgeArea = createKnowledgeAreaDTO(10, 'new long title', 'new title', 'new description', newParentId);
        const expectedKnowledgeAreaInTree = convertToKnowledgeAreaForTree(updatedKnowledgeArea, true, 1);
        const tree: KnowledgeAreaDTO[] = [
            { id: parentId, title: 'title1', children: [knowledgeAreaToUpdate, { id: 3 }] },
            { id: newParentId, title: 'title2', children: [{ id: 4 }] },
        ];

        prepareAndExecuteKnowledgeAreaUpdate(tree, knowledgeAreaToUpdate, updatedKnowledgeArea);

        const knowledgeAreaMap = component['knowledgeAreaMap'];
        const oldParent = knowledgeAreaMap.get(parentId)!;
        expect(oldParent.children).toHaveLength(1);
        expect(oldParent.children).not.toContainEqual([expectedKnowledgeAreaInTree]);
        const newParent = knowledgeAreaMap.get(newParentId)!;
        expect(newParent.children).toHaveLength(2);
        expect(newParent.children).toContainEqual(expectedKnowledgeAreaInTree);
    });

    function prepareAndExecuteCompetencyUpdate(tree: KnowledgeAreaDTO[], competencyToUpdate: StandardizedCompetencyDTO, updatedCompetency: StandardizedCompetencyDTO) {
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        component['selectedCompetency'] = competencyToUpdate;
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const updateSpy = jest.spyOn(adminStandardizedCompetencyService, 'updateStandardizedCompetency');
        updateSpy.mockReturnValue(of(new HttpResponse({ body: updatedCompetency })));
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(StandardizedCompetencyDetailStubComponent)).componentInstance;
        detailComponent.onSave.emit(competencyToUpdate);

        expect(updateSpy).toHaveBeenCalled();
    }

    function prepareAndExecuteKnowledgeAreaUpdate(tree: KnowledgeAreaDTO[], knowledgeAreaToUpdate: KnowledgeAreaDTO, updatedKnowledgeArea: KnowledgeAreaDTO) {
        getForTreeViewSpy.mockReturnValue(of(new HttpResponse({ body: tree })));
        component['selectedKnowledgeArea'] = knowledgeAreaToUpdate;
        const adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        const updateSpy = jest.spyOn(adminStandardizedCompetencyService, 'updateKnowledgeArea');
        updateSpy.mockReturnValue(of(new HttpResponse({ body: updatedKnowledgeArea })));
        componentFixture.detectChanges();

        const detailComponent = componentFixture.debugElement.query(By.directive(KnowledgeAreaDetailStubComponent)).componentInstance;
        detailComponent.onSave.emit(knowledgeAreaToUpdate);

        expect(updateSpy).toHaveBeenCalled();
    }

    function createCompetencyDTO(id?: number, title?: string, description?: string, taxonomy?: CompetencyTaxonomy, knowledgeAreaId?: number) {
        const competency: StandardizedCompetencyDTO = {
            id: id,
            title: title,
            description: description,
            taxonomy: taxonomy,
            knowledgeAreaId: knowledgeAreaId,
        };
        return competency;
    }

    function createKnowledgeAreaDTO(
        id?: number,
        title?: string,
        shortTitle?: string,
        description?: string,
        parentId?: number,
        children?: KnowledgeAreaDTO[],
        competencies?: StandardizedCompetencyDTO[],
    ) {
        const knowledgeArea: KnowledgeAreaDTO = {
            id: id,
            title: title,
            shortTitle: shortTitle,
            description: description,
            parentId: parentId,
            children: children,
            competencies: competencies,
        };
        return knowledgeArea;
    }
});

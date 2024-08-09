import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StandardizedCompetencyFilterStubComponent } from './filter/standardized-competency-filter-stub.component';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { StandardizedCompetencyDetailStubComponent } from './detail/standardized-competency-detail-stub.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { Competency } from 'app/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { StandardizedCompetencyService } from 'app/shared/standardized-competencies/standardized-competency.service';
import { KnowledgeAreaTreeStubComponent } from './knowledge-area-tree-stub.component';
import { SortService } from 'app/shared/service/sort.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Source } from 'app/entities/competency/standardized-competency.model';
import { DocumentationButtonComponent } from 'app/shared/components/documentation-button/documentation-button.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';
import { CourseImportStandardizedPrerequisitesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-prerequisites.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';

describe('CourseImportStandardizedPrerequisitesComponent', () => {
    let componentFixture: ComponentFixture<CourseImportStandardizedPrerequisitesComponent>;
    let component: CourseImportStandardizedPrerequisitesComponent;
    const courseId = 1;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: courseId }) },
    } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                CourseImportStandardizedPrerequisitesComponent,
                ArtemisSharedCommonModule,
                ArtemisSharedComponentModule,
                ArtemisMarkdownModule,
                StandardizedCompetencyFilterComponent,
                StandardizedCompetencyDetailComponent,
                KnowledgeAreaTreeComponent,
                ArtemisTestModule,
            ],
            declarations: [
                StandardizedCompetencyFilterStubComponent,
                StandardizedCompetencyDetailStubComponent,
                KnowledgeAreaTreeStubComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortDirective),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(PrerequisiteService),
                MockProvider(StandardizedCompetencyService),
                MockProvider(SortService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseImportStandardizedPrerequisitesComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load data', () => {
        const dtoTree = [
            {
                id: 1,
                children: [
                    {
                        id: 11,
                        parentId: 1,
                        children: [
                            {
                                id: 111,
                                parentId: 11,
                            },
                        ],
                    },
                ],
            },
            {
                id: 2,
                children: [
                    {
                        id: 21,
                        parentId: 2,
                    },
                ],
                competencies: [
                    {
                        id: 1,
                    },
                ],
            },
        ];
        const sources: Source[] = [{ id: 1 }, { id: 2 }];

        const standardizedCompetencyService = TestBed.inject(StandardizedCompetencyService);
        const getForTreeViewSpy = jest.spyOn(standardizedCompetencyService, 'getAllForTreeView').mockReturnValue(of(new HttpResponse({ body: dtoTree })));
        const getSourcesSpy = jest.spyOn(standardizedCompetencyService, 'getSources').mockReturnValue(of(new HttpResponse({ body: sources })));

        componentFixture.detectChanges();

        expect(getForTreeViewSpy).toHaveBeenCalled();
        expect(getSourcesSpy).toHaveBeenCalled();
        expect(component['knowledgeAreaMap'].size).toBe(5);
        expect(component['knowledgeAreasForSelect']).toHaveLength(5);
        expect(component['sources']).toHaveLength(2);
    });

    it('should open details', () => {
        component['sources'] = [{ id: 1, title: 'title1', author: 'author1' }];
        const competencyToOpen = { id: 2, isVisible: true, selected: true, sourceId: 1 };

        component['openCompetencyDetails'](competencyToOpen);

        expect(component['selectedCompetency']).toEqual(competencyToOpen);
        expect(component['sourceString']).toBeTruthy();
    });

    it('should close details', () => {
        component['selectedCompetency'] = { id: 2, isVisible: true, selected: true };

        component['closeCompetencyDetails']();

        expect(component['selectedCompetency']).toBeUndefined();
    });

    it('should toggle select', () => {
        const competencyToSelect = { id: 2, isVisible: true, selected: true };
        component['selectedCompetencies'] = [{ id: 1, isVisible: true, selected: true }];

        competencyToSelect.selected = true;
        component['toggleSelect'](competencyToSelect);

        expect(component['selectedCompetencies']).toHaveLength(2);

        competencyToSelect.selected = false;
        component['toggleSelect'](competencyToSelect);

        expect(component['selectedCompetencies']).toHaveLength(1);
    });

    it('should deselect', () => {
        const competencyToDeselect = { id: 2, isVisible: true, selected: true };
        component['selectedCompetencies'] = [{ id: 1, isVisible: true, selected: true }, competencyToDeselect];

        component['deselectCompetency'](competencyToDeselect);

        expect(component['selectedCompetencies']).toHaveLength(1);
        expect(competencyToDeselect.selected).toBeFalse();
    });

    it('should sort', () => {
        const sortService = TestBed.inject(SortService);
        const sortSpy = jest.spyOn(sortService, 'sortByProperty');

        component['sortSelected']({ predicate: 'title', ascending: true });

        expect(sortSpy).toHaveBeenCalledWith([], 'title', true);
    });

    it('should navigate after importing competencies', () => {
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const importSpy = jest.spyOn(prerequisiteService, 'importStandardizedCompetencies').mockReturnValue(
            of({
                body: [{ id: 1 }, { id: 2 }],
            } as HttpResponse<Competency[]>),
        );
        component['selectedCompetencies'] = [
            { id: 11, isVisible: true },
            { id: 12, isVisible: true },
        ];

        component['importCompetencies']();

        expect(importSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component['cancel']();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should not deactivate with pending changes', () => {
        const deactivateWarningSpy = jest.spyOn(component as any, 'canDeactivateWarning', 'get');

        component['isLoading'] = false;
        component['unloadNotification']({ returnValue: '' });
        expect(deactivateWarningSpy).not.toHaveBeenCalled();

        component['isLoading'] = true;
        component['unloadNotification']({ returnValue: '' });
        expect(deactivateWarningSpy).toHaveBeenCalled();
    });
});

import { ArtemisTestModule } from '../../test.module';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseImportStandardizedCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-competencies.component';
import { StandardizedCompetencyFilterStubComponent } from './filter/standardized-competency-filter-stub.component';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { StandardizedCompetencyDetailStubComponent } from './detail/standardized-competency-detail-stub.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { Competency } from 'app/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { StandardizedCompetencyService } from 'app/shared/standardized-competencies/standardized-competency.service';
import { KnowledgeAreaTreeStubComponent } from './knowledge-area-tree-stub.component';
import { SortService } from 'app/shared/service/sort.service';

describe('CourseImportStandardizedCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<CourseImportStandardizedCompetenciesComponent>;
    let component: CourseImportStandardizedCompetenciesComponent;
    const courseId = 1;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: courseId }) },
    } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CourseImportStandardizedCompetenciesComponent,
                StandardizedCompetencyFilterStubComponent,
                StandardizedCompetencyDetailStubComponent,
                KnowledgeAreaTreeStubComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortDirective),
                MockPipe(HtmlForMarkdownPipe),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
                MockProvider(StandardizedCompetencyService),
                MockProvider(SortService),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseImportStandardizedCompetenciesComponent);
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
            },
        ];

        const standardizedCompetencyService = TestBed.inject(StandardizedCompetencyService);
        const getForTreeViewSpy = jest.spyOn(standardizedCompetencyService, 'getAllForTreeView').mockReturnValue(of(new HttpResponse({ body: dtoTree })));

        componentFixture.detectChanges();

        expect(getForTreeViewSpy).toHaveBeenCalled();
        expect(component['knowledgeAreaMap'].size).toBe(5);
        expect(component['knowledgeAreasForSelect']).toHaveLength(5);
    });

    it('should open details', () => {
        const competencyToOpen = { id: 2, isVisible: true, selected: true };

        component['openCompetencyDetails'](competencyToOpen);

        expect(component['selectedCompetency']).toEqual(competencyToOpen);
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
        const competencyService = TestBed.inject(CompetencyService);
        const importSpy = jest.spyOn(competencyService, 'importStandardizedCompetencies').mockReturnValue(
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
        component['isLoading'] = true;
        expect(component['canDeactivate']()).toBeFalse();

        component['isLoading'] = false;
        component['selectedCompetencies'] = [{ id: 1, isVisible: true }];
        expect(component['canDeactivate']()).toBeFalse();
    });
});

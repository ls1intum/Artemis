import { vi } from 'vitest';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseImportStandardizedCompetenciesComponent } from 'app/atlas/manage/import-standardized-competencies/course-import-standardized-competencies.component';
import { StandardizedCompetencyFilterStubComponent } from 'test/helpers/stubs/atlas/standardized-competency-filter-stub.component';
import { SortDirective } from 'app/foundation/sort/directive/sort.directive';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { StandardizedCompetencyDetailStubComponent } from 'test/helpers/stubs/atlas/standardized-competency-detail-stub.component';
import { ButtonComponent } from 'app/shared-ui/components/buttons/button/button.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AlertService } from 'app/foundation/service/alert.service';
import { KnowledgeAreaTreeStubComponent } from 'test/helpers/stubs/atlas/knowledge-area-tree-stub.component';
import { SortService } from 'app/foundation/service/sort.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Source } from 'app/openapi/model/source';
import { DocumentationButtonComponent } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';
import { StandardizedCompetencyService } from 'app/atlas/shared/standardized-competencies/standardized-competency.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CourseImportStandardizedCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<CourseImportStandardizedCompetenciesComponent>;
    let component: CourseImportStandardizedCompetenciesComponent;
    const courseId = 1;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: courseId }) },
    } as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                CourseImportStandardizedCompetenciesComponent,
                StandardizedCompetencyFilterStubComponent,
                StandardizedCompetencyDetailStubComponent,
                KnowledgeAreaTreeStubComponent,
                FaIconComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortDirective),
                MockDirective(MarkdownDirective),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(CompetencyService),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AlertService),
                MockProvider(SortService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseImportStandardizedCompetenciesComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        TestBed.inject(HttpTestingController).verify();
        vi.restoreAllMocks();
    });

    it('should load data', async () => {
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
        const getForTreeViewSpy = vi.spyOn(standardizedCompetencyService, 'getAllForTreeView');
        const getSourcesSpy = vi.spyOn(standardizedCompetencyService, 'getSources');

        componentFixture.detectChanges();
        expect(componentFixture.nativeElement.querySelector('h2').textContent).toBe('artemisApp.standardizedCompetency.courseImport.title');

        TestBed.tick();
        const http = TestBed.inject(HttpTestingController);
        const treeRequest = http.expectOne('/api/atlas/standardized-competencies/for-tree-view');
        const sourcesRequest = http.expectOne('/api/atlas/standardized-competencies/sources');
        expect(treeRequest.request.method).toBe('GET');
        expect(sourcesRequest.request.method).toBe('GET');
        expect(component['isLoading']()).toBe(true);
        sourcesRequest.flush(sources);
        TestBed.tick();
        expect(component['knowledgeAreaMap'].size).toBe(0);
        expect(component['sources']).toEqual([]);
        expect(component['isLoading']()).toBe(true);
        treeRequest.flush(dtoTree);
        await componentFixture.whenStable();
        expect(component['isLoading']()).toBe(false);
        expect(getForTreeViewSpy).toHaveBeenCalledExactlyOnceWith();
        expect(getSourcesSpy).not.toHaveBeenCalled();
        http.expectNone('/api/atlas/standardized-competencies/sources');
        expect(component['knowledgeAreaMap'].size).toBe(5);
        expect(component['knowledgeAreasForSelect']).toHaveLength(5);
        expect(component['sources']).toHaveLength(2);
    });

    it('accepts empty results and waits for delayed sources', async () => {
        componentFixture.detectChanges();
        TestBed.tick();
        const http = TestBed.inject(HttpTestingController);
        http.expectOne('/api/atlas/standardized-competencies/for-tree-view').flush([]);
        expect(component['isLoading']()).toBe(true);
        expect(component.canDeactivate()).toBe(false);
        http.expectOne('/api/atlas/standardized-competencies/sources').flush([]);
        await componentFixture.whenStable();
        expect(component['isLoading']()).toBe(false);
        expect(component['dataSource'].data).toEqual([]);
        expect(component['sources']).toEqual([]);
        expect(component.canDeactivate()).toBe(true);
    });

    it.each([403, 500])('propagates sources error %i and cancels the parallel tree read', async (status) => {
        const alert = vi.spyOn(TestBed.inject(AlertService), 'error');
        componentFixture.detectChanges();
        TestBed.tick();
        const http = TestBed.inject(HttpTestingController);
        const tree = http.expectOne('/api/atlas/standardized-competencies/for-tree-view');
        http.expectOne('/api/atlas/standardized-competencies/sources').flush({ message: 'Error' }, { status, statusText: 'Error' });
        await componentFixture.whenStable();
        expect(tree.cancelled).toBe(true);
        expect(component['knowledgeAreaMap'].size).toBe(0);
        expect(component['sources']).toEqual([]);
        // Existing loading-on-error behavior is retained; a wider UX fix is outside this migration.
        expect(component['isLoading']()).toBe(true);
        if (status === 403) {
            expect(alert).toHaveBeenCalledExactlyOnceWith('error.http.403');
        } else {
            expect(alert).not.toHaveBeenCalled();
        }
    });

    it('cancels sources when the parallel tree fails', async () => {
        componentFixture.detectChanges();
        TestBed.tick();
        const http = TestBed.inject(HttpTestingController);
        const sources = http.expectOne('/api/atlas/standardized-competencies/sources');
        http.expectOne('/api/atlas/standardized-competencies/for-tree-view').flush({}, { status: 403, statusText: 'Forbidden' });
        await componentFixture.whenStable();
        expect(sources.cancelled).toBe(true);
        expect(component['isLoading']()).toBe(true);
        expect(TestBed.inject(AlertService).error).toHaveBeenCalledExactlyOnceWith('error.http.403');
    });

    it('cancels on destruction and starts fresh on re-entry', async () => {
        componentFixture.detectChanges();
        TestBed.tick();
        const http = TestBed.inject(HttpTestingController);
        const tree = http.expectOne('/api/atlas/standardized-competencies/for-tree-view');
        const sources = http.expectOne('/api/atlas/standardized-competencies/sources');
        componentFixture.destroy();
        expect(tree.cancelled).toBe(true);
        expect(sources.cancelled).toBe(true);
        componentFixture = TestBed.createComponent(CourseImportStandardizedCompetenciesComponent);
        component = componentFixture.componentInstance;
        componentFixture.detectChanges();
        TestBed.tick();
        expect(component['sources']).toEqual([]);
        http.expectOne('/api/atlas/standardized-competencies/for-tree-view').flush([{ id: 42 }]);
        http.expectOne('/api/atlas/standardized-competencies/sources').flush([{ id: 7, title: 'Fresh' }]);
        await componentFixture.whenStable();
        expect(component['knowledgeAreaMap'].has(42)).toBe(true);
        expect(component['sources']).toEqual([{ id: 7, title: 'Fresh', author: undefined, uri: undefined }]);
        expect(component['isLoading']()).toBe(false);
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
        expect(competencyToDeselect.selected).toBeFalsy();
    });

    it('should sort', () => {
        const sortService = TestBed.inject(SortService);
        const sortSpy = vi.spyOn(sortService, 'sortByProperty');

        component['sortSelected']({ predicate: 'title', ascending: true });

        expect(sortSpy).toHaveBeenCalledWith([], 'title', true);
    });

    it('should navigate after importing competencies', () => {
        const router: Router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');
        const competencyService = TestBed.inject(CompetencyService);
        const importSpy = vi.spyOn(competencyService, 'importStandardizedCompetencies').mockReturnValue(
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
        const navigateSpy = vi.spyOn(router, 'navigate');

        component['cancel']();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should not deactivate with pending changes', () => {
        let canDeactivate;

        component['isLoading'].set(false);
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeTruthy();

        component['isLoading'].set(true);
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeFalsy();
    });
});

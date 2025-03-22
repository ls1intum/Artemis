import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { AdminImportStandardizedCompetenciesComponent } from 'app/core/admin/standardized-competencies/import/admin-import-standardized-competencies.component';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { KnowledgeAreaTreeStubComponent } from './knowledge-area-tree-stub.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/shared/service/alert.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { AdminStandardizedCompetencyService } from 'app/core/admin/standardized-competencies/admin-standardized-competency.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { KnowledgeAreasForImportDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from '../../helpers/mocks/activated-route/mock-activated-route';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

describe('AdminImportStandardizedCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<AdminImportStandardizedCompetenciesComponent>;
    let component: AdminImportStandardizedCompetenciesComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                MockPipe(HtmlForMarkdownPipe),
                KnowledgeAreaTreeStubComponent,
                MockComponent(ButtonComponent),
                MockComponent(StandardizedCompetencyDetailComponent),
                MockModule(FontAwesomeModule),
                MockComponent(StandardizedCompetencyDetailComponent),
                MockComponent(KnowledgeAreaTreeComponent),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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

    it.each([[[new File([''], 'f.txt')]], [[new File([''], 'f1.json'), new File([''], 'f2.json')]], [[{ name: 'f.json', size: MAX_FILE_SIZE + 1 } as File]]])(
        'should show error for invalid files',
        (files) => {
            const mockAlertService = TestBed.inject(AlertService);
            const errorSpy = jest.spyOn(mockAlertService, 'error');

            //explicitly use any to avoid problems with event type
            const event: any = {
                target: {
                    files: files,
                },
            };

            component.onFileChange(event);
            expect(errorSpy).toHaveBeenCalled();
        },
    );

    it('should not show error for valid file', () => {
        const mockAlertService = TestBed.inject(AlertService);
        const errorSpy = jest.spyOn(mockAlertService, 'error');

        //explicitly use any to avoid problems with event type
        const event: any = {
            target: {
                files: [new File([''], 'f1.json')],
            },
        };

        component.onFileChange(event);
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it.each([['invalid json syntax'], ['{"invalid": "because no knowledgeAreas"}'], ['{"knowledgeAreas": "invalid because this is not an array"}']])(
        'should not set import data for invalid json',
        (result) => {
            const mockAlertService = TestBed.inject(AlertService);
            const errorSpy = jest.spyOn(mockAlertService, 'error');
            component['fileReader'] = {
                result: result,
            } as FileReader;

            component['setImportDataAndCount']();

            expect(errorSpy).toHaveBeenCalled();
            expect(component['importData']).toBeUndefined();
        },
    );

    it('should set import data', () => {
        const result: KnowledgeAreasForImportDTO = {
            knowledgeAreas: [
                {
                    title: 'ka1',
                    children: [
                        {
                            title: 'ka2',
                            children: [{ title: 'ka3' }],
                            competencies: [{ title: 'c4' }, { title: 'c5' }],
                        },
                    ],
                    competencies: [{ title: 'c1' }, { title: 'c2' }, { title: 'c3' }],
                },
                {
                    title: 'ka4',
                    children: [],
                    competencies: [],
                },
            ],
            sources: [
                {
                    id: 1,
                    title: 'any source',
                },
            ],
        };
        component['fileReader'] = {
            result: JSON.stringify(result),
        } as FileReader;
        const expectedCount = { knowledgeAreas: 4, competencies: 5 };

        component['setImportDataAndCount']();

        expect(component['importCount']).toEqual(expectedCount);
    });

    it('should navigate on successful competency import', () => {
        const mockRouter = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(mockRouter, 'navigate');
        const competencyService = TestBed.inject(AdminStandardizedCompetencyService);
        jest.spyOn(competencyService, 'importStandardizedCompetencyCatalog').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));

        component.importCompetencies();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        const mockRouter = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(mockRouter, 'navigate');

        component.cancel();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should toggle collapse', () => {
        component['isCollapsed'] = false;

        component.toggleCollapse();

        expect(component['isCollapsed']).toBeTrue();
    });

    it('should open details', () => {
        component['importData'] = { knowledgeAreas: [], sources: [{ id: 1, title: 'any source' }] };
        const competencyToOpen = { id: 2, isVisible: true, sourceId: 1 };
        const knowledgeAreaTitle = 'knowledgeArea';

        component['openCompetencyDetails'](competencyToOpen, knowledgeAreaTitle);

        expect(component['selectedCompetency']).toEqual(competencyToOpen);
        expect(component['knowledgeAreaTitle']).toEqual(knowledgeAreaTitle);
        expect(component['sourceString']).toBeTruthy();
    });

    it('should close details', () => {
        component['selectedCompetency'] = { id: 2, isVisible: true };

        component['closeCompetencyDetails']();

        expect(component['selectedCompetency']).toBeUndefined();
        expect(component['knowledgeAreaTitle']).toBe('');
    });
});

/**
 * Vitest tests for AdminImportStandardizedCompetenciesComponent.
 * Tests the import functionality for standardized competencies from JSON files.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockComponent, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

import { AdminImportStandardizedCompetenciesComponent } from 'app/core/admin/standardized-competencies/import/admin-import-standardized-competencies.component';
import { AdminStandardizedCompetencyService } from 'app/core/admin/standardized-competencies/admin-standardized-competency.service';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { AlertService } from 'app/shared/service/alert.service';
import { MAX_FILE_SIZE } from 'app/shared/constants/input.constants';
import { KnowledgeAreasForImportDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';

describe('AdminImportStandardizedCompetenciesComponent', () => {
    setupTestBed({ zoneless: true });

    let componentFixture: ComponentFixture<AdminImportStandardizedCompetenciesComponent>;
    let component: AdminImportStandardizedCompetenciesComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AdminImportStandardizedCompetenciesComponent],
            providers: [
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(AdminImportStandardizedCompetenciesComponent, {
                set: {
                    imports: [
                        MockModule(FontAwesomeModule),
                        MockComponent(StandardizedCompetencyDetailComponent),
                        MockComponent(KnowledgeAreaTreeComponent),
                        MockComponent(ButtonComponent),
                        MockPipe(HtmlForMarkdownPipe),
                    ],
                },
            })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(AdminImportStandardizedCompetenciesComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([[[new File([''], 'f.txt')]], [[new File([''], 'f1.json'), new File([''], 'f2.json')]], [[{ name: 'f.json', size: MAX_FILE_SIZE + 1 } as File]]])(
        'should show error for invalid files',
        (files) => {
            const mockAlertService = TestBed.inject(AlertService);
            const errorSpy = vi.spyOn(mockAlertService, 'error');

            // Explicitly use any to avoid problems with event type
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
        const errorSpy = vi.spyOn(mockAlertService, 'error');

        // Explicitly use any to avoid problems with event type
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
            const errorSpy = vi.spyOn(mockAlertService, 'error');
            component['fileReader'] = {
                result: result,
            } as FileReader;

            component['setImportDataAndCount']();

            expect(errorSpy).toHaveBeenCalled();
            expect(component['importData']()).toBeUndefined();
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

        expect(component['importCount']()).toEqual(expectedCount);
    });

    it('should navigate on successful competency import', () => {
        const mockRouter = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(mockRouter, 'navigate');
        const competencyService = TestBed.inject(AdminStandardizedCompetencyService);
        vi.spyOn(competencyService, 'importStandardizedCompetencyCatalog').mockReturnValue(of(new HttpResponse<void>({ status: 200 })));

        component.importCompetencies();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        const mockRouter = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(mockRouter, 'navigate');

        component.cancel();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should toggle collapse', () => {
        component['isCollapsed'].set(false);

        component.toggleCollapse();

        expect(component['isCollapsed']()).toBe(true);
    });

    it('should open details', () => {
        component['importData'].set({ knowledgeAreas: [], sources: [{ id: 1, title: 'any source' }] });
        const competencyToOpen = { id: 2, isVisible: true, sourceId: 1 };
        const knowledgeAreaTitle = 'knowledgeArea';

        component['openCompetencyDetails'](competencyToOpen, knowledgeAreaTitle);

        expect(component['selectedCompetency']()).toEqual(competencyToOpen);
        expect(component['knowledgeAreaTitle']()).toEqual(knowledgeAreaTitle);
        expect(component['sourceString']()).toBeTruthy();
    });

    it('should close details', () => {
        component['selectedCompetency'].set({ id: 2, isVisible: true });

        component['closeCompetencyDetails']();

        expect(component['selectedCompetency']()).toBeUndefined();
        expect(component['knowledgeAreaTitle']()).toBe('');
    });
});

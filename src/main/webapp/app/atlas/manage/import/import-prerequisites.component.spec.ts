import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImportCompetenciesComponent } from 'app/atlas/manage/import/import-competencies.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { CompetencyWithTailRelationDTO } from 'app/atlas/shared/entities/competency.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ImportPrerequisitesComponent } from 'app/atlas/manage/import/import-prerequisites.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ImportPrerequisitesComponent', () => {
    setupTestBed({ zoneless: true });
    let componentFixture: ComponentFixture<ImportPrerequisitesComponent>;
    let component: ImportPrerequisitesComponent;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ImportCompetenciesComponent],
            declarations: [],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
                    } as ActivatedRoute,
                },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportPrerequisitesComponent);
                component = componentFixture.componentInstance;
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should import prerequisites on submit', () => {
        const competencyDTOs: CompetencyWithTailRelationDTO[] = [{ competency: { id: 1 } }, { competency: { id: 2 } }];
        const importBulkSpy = vi.spyOn(prerequisiteService, 'importBulk').mockReturnValue(
            of({
                body: competencyDTOs,
            } as HttpResponse<CompetencyWithTailRelationDTO[]>),
        );
        const router: Router = TestBed.inject(Router);
        const navigateSpy = vi.spyOn(router, 'navigate');

        component.onSubmit();

        expect(importBulkSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });
});

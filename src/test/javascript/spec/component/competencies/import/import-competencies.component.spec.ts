import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ImportCompetenciesComponent } from 'app/course/competencies/import/import-competencies.component';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { CompetencyWithTailRelationDTO } from 'app/entities/competency.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ImportCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesComponent>;
    let component: ImportCompetenciesComponent;
    let competencyService: CompetencyService;

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
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ImportCompetenciesComponent);
                component = componentFixture.componentInstance;
                competencyService = TestBed.inject(CompetencyService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should import competencies on submit', () => {
        const competencyDTOs: CompetencyWithTailRelationDTO[] = [{ competency: { id: 1 } }, { competency: { id: 2 } }];
        const importBulkSpy = jest.spyOn(competencyService, 'importBulk').mockReturnValue(
            of({
                body: competencyDTOs,
            } as HttpResponse<CompetencyWithTailRelationDTO[]>),
        );
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onSubmit();

        expect(importBulkSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });
});

import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ImportCompetenciesComponent } from 'app/course/competencies/import-competencies/import-competencies.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { of } from 'rxjs';
import { CompetencyWithTailRelationDTO } from 'app/entities/competency.model';
import { HttpResponse } from '@angular/common/http';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import-competencies/import-competencies-table.component';
import { CompetencySearchComponent } from 'app/course/competencies/import-competencies/competency-search.component';

describe('ImportCompetenciesComponent', () => {
    let componentFixture: ComponentFixture<ImportCompetenciesComponent>;
    let component: ImportCompetenciesComponent;
    let competencyService: CompetencyService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ImportCompetenciesComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockComponent(ImportCompetenciesTableComponent),
                MockComponent(CompetencySearchComponent),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
                    } as ActivatedRoute,
                },
                { provide: Router, useClass: MockRouter },
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

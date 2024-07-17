import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ImportPrerequisitesComponent } from 'app/course/competencies/import/import-prerequisites.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { FormsModule } from 'app/forms/forms.module';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { ImportCompetenciesTableComponent } from 'app/course/competencies/import/import-competencies-table.component';
import { CompetencySearchComponent } from 'app/course/competencies/import/competency-search.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { HttpResponse } from '@angular/common/http';
import { CompetencyWithTailRelationDTO } from 'app/entities/competency.model';

describe('ImportPrerequisitesComponent', () => {
    let componentFixture: ComponentFixture<ImportPrerequisitesComponent>;
    let component: ImportPrerequisitesComponent;
    let prerequisiteService: PrerequisiteService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                ImportPrerequisitesComponent,
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
                componentFixture = TestBed.createComponent(ImportPrerequisitesComponent);
                component = componentFixture.componentInstance;
                prerequisiteService = TestBed.inject(PrerequisiteService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should import prerequisites on submit', () => {
        component.courseId = 1;
        component.selectedCourseCompetencies = {
            resultsOnPage: [
                { id: 1, title: 'competency1' },
                { id: 2, title: 'competency2' },
            ],
            numberOfPages: 0,
        };
        const importBulkSpy = jest.spyOn(prerequisiteService, 'importBulk').mockReturnValue(
            of(
                new HttpResponse({
                    body: [
                        { competency: { id: 11, title: 'competency1' } } as CompetencyWithTailRelationDTO,
                        { competency: { id: 12, title: 'competency2' } } as CompetencyWithTailRelationDTO,
                    ],
                    status: 200,
                }),
            ),
        );
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component.onSubmit();

        expect(importBulkSpy).toHaveBeenCalledWith([1, 2], 1);
        expect(navigateSpy).toHaveBeenCalled();
    });
});

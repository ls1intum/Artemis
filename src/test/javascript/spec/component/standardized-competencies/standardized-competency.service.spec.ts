import { HttpResponse } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { StandardizedCompetencyService } from 'app/admin/standardized-competencies/standardized-competency.service';
import { KnowledgeArea, KnowledgeAreaDTO, StandardizedCompetency } from 'app/entities/competency/standardized-competency.model';
import { take } from 'rxjs';
import { CompetencyTaxonomy } from 'app/entities/competency.model';

describe('StandardizedCompetencyService', () => {
    let standardizedCompetencyService: StandardizedCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultStandardizedCompetency: StandardizedCompetency;
    let defaultKnowledgeArea: KnowledgeArea;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [],
        });

        standardizedCompetencyService = TestBed.inject(StandardizedCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultStandardizedCompetency = {
            id: 1,
            title: 'Standardized Competency1',
            taxonomy: CompetencyTaxonomy.ANALYZE,
        };
        defaultKnowledgeArea = {
            id: 1,
            title: 'Knowledge Area1',
        };
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should get competency', fakeAsync(() => {
        let actualCompetency = new HttpResponse<StandardizedCompetency>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetency = { ...expectedCompetency };

        standardizedCompetencyService
            .getStandardizedCompetency(1)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualCompetency.body).toEqual(expectedCompetency);
    }));

    it('should get a knowledge area', fakeAsync(() => {
        let actualKnowledgeArea = new HttpResponse<KnowledgeArea>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeArea = { ...expectedKnowledgeArea };

        standardizedCompetencyService
            .getKnowledgeArea(1)
            .pipe(take(1))
            .subscribe((resp) => (actualKnowledgeArea = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualKnowledgeArea.body).toEqual(expectedKnowledgeArea);
    }));

    it('should get all for tree view', fakeAsync(() => {
        let actualResult = new HttpResponse<KnowledgeAreaDTO[]>();
        const expectedResult: KnowledgeAreaDTO[] = [
            {
                id: 1,
                title: 'KnowledgeAreaDTO1',
                children: [
                    {
                        id: 11,
                        title: 'KnowledgeAreaDTO1.1',
                    },
                ],
                competencies: [
                    {
                        id: 1,
                        title: 'Standardized Competency',
                    },
                ],
            },
            {
                id: 2,
                title: 'KnowledgeAreaDTO2',
            },
        ];
        const returnedFromService: KnowledgeAreaDTO[] = [...expectedResult];

        standardizedCompetencyService
            .getAllForTreeView()
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualResult.body).toEqual(expectedResult);
    }));
});

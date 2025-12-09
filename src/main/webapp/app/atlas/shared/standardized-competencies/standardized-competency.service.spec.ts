import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { KnowledgeAreaDTO, Source, StandardizedCompetency } from 'app/atlas/shared/entities/standardized-competency.model';
import { take } from 'rxjs';
import { StandardizedCompetencyService } from 'app/atlas/shared/standardized-competencies/standardized-competency.service';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

describe('StandardizedCompetencyService', () => {
    let standardizedCompetencyService: StandardizedCompetencyService;
    let httpTestingController: HttpTestingController;
    let defaultStandardizedCompetency: StandardizedCompetency;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });

        standardizedCompetencyService = TestBed.inject(StandardizedCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultStandardizedCompetency = {
            id: 1,
            title: 'Standardized Competency1',
            taxonomy: CompetencyTaxonomy.ANALYZE,
        };
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should get competency', fakeAsync(() => {
        let actualCompetency = new HttpResponse<StandardizedCompetency>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetency = Object.assign({}, expectedCompetency);

        standardizedCompetencyService
            .getStandardizedCompetency(1)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualCompetency.body).toEqual(expectedCompetency);
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

    it('should get sources', fakeAsync(() => {
        let actualSources = new HttpResponse<Source[]>();
        const expectedSources: Source[] = [
            { id: 1, title: 'source1' },
            { id: 2, title: 'source2' },
        ];
        const returnedFromService = [...expectedSources];

        standardizedCompetencyService
            .getSources()
            .pipe(take(1))
            .subscribe((resp) => (actualSources = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();

        expect(actualSources.body).toEqual(expectedSources);
    }));
});

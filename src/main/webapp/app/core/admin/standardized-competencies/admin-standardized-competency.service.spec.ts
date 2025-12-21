/**
 * Vitest tests for AdminStandardizedCompetencyService.
 * Tests the service methods for CRUD operations on standardized competencies and knowledge areas.
 */
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs';

import { AdminStandardizedCompetencyService } from 'app/core/admin/standardized-competencies/admin-standardized-competency.service';
import { KnowledgeAreaDTO, KnowledgeAreasForImportDTO, StandardizedCompetencyDTO } from 'app/atlas/shared/entities/standardized-competency.model';
import { CompetencyTaxonomy } from 'app/atlas/shared/entities/competency.model';

describe('AdminStandardizedCompetencyService', () => {
    setupTestBed({ zoneless: true });

    let adminStandardizedCompetencyService: AdminStandardizedCompetencyService;
    let httpTestingController: HttpTestingController;

    /** Default test knowledge area */
    let defaultKnowledgeArea: KnowledgeAreaDTO;
    /** Default test standardized competency */
    let defaultStandardizedCompetency: StandardizedCompetencyDTO;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();

        adminStandardizedCompetencyService = TestBed.inject(AdminStandardizedCompetencyService);
        httpTestingController = TestBed.inject(HttpTestingController);

        defaultKnowledgeArea = {
            id: 1,
            title: 'Knowledge Area1',
        };
        defaultStandardizedCompetency = {
            id: 1,
            title: 'Standardized Competency1',
            taxonomy: CompetencyTaxonomy.ANALYZE,
            knowledgeAreaId: defaultKnowledgeArea.id,
        };
    });

    afterEach(() => {
        httpTestingController.verify();
    });

    it('should create competency', async () => {
        let actualCompetency = new HttpResponse<StandardizedCompetencyDTO>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetencyDTO = { ...expectedCompetency };

        adminStandardizedCompetencyService
            .createStandardizedCompetency(expectedCompetency)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(actualCompetency.body).toEqual(expectedCompetency);
    });

    it('should update competency', async () => {
        let actualCompetency = new HttpResponse<StandardizedCompetencyDTO>();
        const expectedCompetency = defaultStandardizedCompetency;
        const returnedFromService: StandardizedCompetencyDTO = { ...expectedCompetency };

        adminStandardizedCompetencyService
            .updateStandardizedCompetency(expectedCompetency)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);

        expect(actualCompetency.body).toEqual(expectedCompetency);
    });

    it('should delete competency', async () => {
        let actualResult = new HttpResponse<void>();

        adminStandardizedCompetencyService
            .deleteStandardizedCompetency(1)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        expect(actualResult.status).toBe(200);
    });

    it('should create knowledge area', async () => {
        let actualKnowledgeArea = new HttpResponse<KnowledgeAreaDTO>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeAreaDTO = { ...expectedKnowledgeArea };

        adminStandardizedCompetencyService
            .createKnowledgeArea(expectedKnowledgeArea)
            .pipe(take(1))
            .subscribe((resp) => (actualKnowledgeArea = resp));

        const req = httpTestingController.expectOne({ method: 'POST' });
        req.flush(returnedFromService);

        expect(actualKnowledgeArea.body).toEqual(expectedKnowledgeArea);
    });

    it('should update knowledge area', async () => {
        let actualCompetency = new HttpResponse<KnowledgeAreaDTO>();
        const expectedKnowledgeArea = defaultKnowledgeArea;
        const returnedFromService: KnowledgeAreaDTO = { ...expectedKnowledgeArea };

        adminStandardizedCompetencyService
            .updateKnowledgeArea(expectedKnowledgeArea)
            .pipe(take(1))
            .subscribe((resp) => (actualCompetency = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);

        expect(actualCompetency.body).toEqual(expectedKnowledgeArea);
    });

    it('should delete knowledge area', async () => {
        let actualResult = new HttpResponse<void>();

        adminStandardizedCompetencyService
            .deleteKnowledgeArea(1)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'DELETE' });
        req.flush({ status: 200 });

        expect(actualResult.status).toBe(200);
    });

    it('should import competencies', async () => {
        let actualResult = new HttpResponse<void>();
        const requestBody: KnowledgeAreasForImportDTO = {
            knowledgeAreas: [],
            sources: [],
        };

        adminStandardizedCompetencyService
            .importStandardizedCompetencyCatalog(requestBody)
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'PUT' });
        req.flush({ status: 200 });

        expect(actualResult.status).toBe(200);
    });

    it('should export competencies', async () => {
        let actualResult = new HttpResponse<string>();
        const expectedResult: string = '{ knowledgeAreas: [], sources: [] }';
        const returnedFromService = expectedResult;

        adminStandardizedCompetencyService
            .exportStandardizedCompetencyCatalog()
            .pipe(take(1))
            .subscribe((resp) => (actualResult = resp));

        const req = httpTestingController.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(actualResult.body).toEqual(expectedResult);
    });
});

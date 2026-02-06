import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { provideHttpClient } from '@angular/common/http';
import { tutorialGroupConfigurationDtoFromEntity } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration-dto.model';

describe('TutorialGroupsConfigurationService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupsConfigurationService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupsConfiguration;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupsConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroupsConfiguration({});
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('getOneOfCourse', () => {
        const returnedFromService = { ...elemDefault };
        let result: any;
        service
            .getOneOfCourse(1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: elemDefault });
    });

    it('create', () => {
        const returnedFromService = { ...tutorialGroupConfigurationDtoFromEntity(elemDefault), id: 0 };
        const expected = { ...returnedFromService };
        let result: any;
        service
            .create(tutorialGroupConfigurationDtoFromEntity(new TutorialGroupsConfiguration()), 1, [])
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    it('update', () => {
        const returnedFromService = { ...tutorialGroupConfigurationDtoFromEntity(elemDefault) };
        const expected = { ...returnedFromService };
        let result: any;

        service
            .update(1, 1, tutorialGroupConfigurationDtoFromEntity(new TutorialGroupsConfiguration()), [])
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        expect(result).toMatchObject({ body: expected });
    });

    describe('convertTutorialGroupsConfigurationDatesFromServer', () => {
        it('should convert dates from server without free periods', () => {
            const configuration = new TutorialGroupsConfiguration();
            configuration.id = 1;
            configuration.tutorialPeriodStartInclusive = '2021-01-01' as any;
            configuration.tutorialPeriodEndInclusive = '2021-02-01' as any;

            const result = service.convertTutorialGroupsConfigurationDatesFromServer(configuration);

            expect(result.tutorialPeriodStartInclusive).toBeDefined();
            expect(result.tutorialPeriodEndInclusive).toBeDefined();
        });

        it('should convert dates from server with free periods', () => {
            const freePeriod1 = new TutorialGroupFreePeriod();
            freePeriod1.id = 1;
            freePeriod1.start = '2021-01-10' as any;
            freePeriod1.end = '2021-01-15' as any;
            freePeriod1.reason = 'Holiday';

            const freePeriod2 = new TutorialGroupFreePeriod();
            freePeriod2.id = 2;
            freePeriod2.start = '2021-01-20' as any;
            freePeriod2.end = '2021-01-25' as any;
            freePeriod2.reason = 'Break';

            const configuration = new TutorialGroupsConfiguration();
            configuration.id = 1;
            configuration.tutorialPeriodStartInclusive = '2021-01-01' as any;
            configuration.tutorialPeriodEndInclusive = '2021-02-01' as any;
            configuration.tutorialGroupFreePeriods = [freePeriod1, freePeriod2];

            const result = service.convertTutorialGroupsConfigurationDatesFromServer(configuration);

            expect(result.tutorialPeriodStartInclusive).toBeDefined();
            expect(result.tutorialPeriodEndInclusive).toBeDefined();
            expect(result.tutorialGroupFreePeriods).toHaveLength(2);
            expect(result.tutorialGroupFreePeriods![0].start).toBeDefined();
            expect(result.tutorialGroupFreePeriods![0].end).toBeDefined();
            expect(result.tutorialGroupFreePeriods![1].start).toBeDefined();
            expect(result.tutorialGroupFreePeriods![1].end).toBeDefined();
        });
    });

    it('should convert dates from server in getOneOfCourse response with free periods', () => {
        const freePeriod = {
            id: 1,
            start: '2021-01-10',
            end: '2021-01-15',
            reason: 'Holiday',
        };

        const returnedFromService = {
            ...elemDefault,
            tutorialPeriodStartInclusive: '2021-01-01',
            tutorialPeriodEndInclusive: '2021-02-01',
            tutorialGroupFreePeriods: [freePeriod],
        };

        let result: any;
        service
            .getOneOfCourse(1)
            .pipe(take(1))
            .subscribe((resp) => (result = resp));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);

        expect(result.body).toBeDefined();
        expect(result.body.tutorialGroupFreePeriods).toHaveLength(1);
    });
});

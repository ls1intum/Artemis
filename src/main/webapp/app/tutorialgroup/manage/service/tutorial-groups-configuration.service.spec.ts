import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { generateExampleTutorialGroupsConfigurationDTO } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/manage/service/tutorial-groups-configuration.service';
import { TutorialGroupConfiguration } from 'app/openapi/model/tutorial-group-configuration';

const CONFIGURATIONS_URL = '/api/tutorialgroup/courses/1/tutorial-groups-configurations';

describe('TutorialGroupsConfigurationService', () => {
    let service: TutorialGroupsConfigurationService;
    let httpMock: HttpTestingController;
    const loaded: TutorialGroupConfiguration = {
        ...generateExampleTutorialGroupsConfigurationDTO({ id: 5 }),
        tutorialGroupFreePeriods: [{ id: 1, start: '2021-01-10T00:00:00Z', end: '2021-01-15T00:00:00Z', reason: 'Holiday' }],
    };
    const settings = { period: [new Date(2021, 0, 1), new Date(2021, 1, 1)], useTutorialGroupChannels: true, usePublicTutorialGroupChannels: false };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting()],
        });
        service = TestBed.inject(TutorialGroupsConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should get the configuration of a course', () => {
        let result: TutorialGroupConfiguration | undefined;
        service.getOneOfCourse(1).subscribe((configuration) => (result = configuration));

        httpMock.expectOne({ method: 'GET', url: CONFIGURATIONS_URL }).flush(loaded);
        expect(result).toEqual(loaded);
    });

    it('should emit undefined when the course has no configuration', () => {
        let result: TutorialGroupConfiguration | undefined = loaded;
        service.getOneOfCourse(1).subscribe((configuration) => (result = configuration));

        httpMock.expectOne({ method: 'GET', url: CONFIGURATIONS_URL }).flush(null);
        expect(result).toBeUndefined();
    });

    it('should create a configuration from the form settings', () => {
        let result: TutorialGroupConfiguration | undefined;
        service.create(1, settings).subscribe((configuration) => (result = configuration));

        const req = httpMock.expectOne({ method: 'POST', url: CONFIGURATIONS_URL });
        expect(req.request.body).toEqual({
            id: undefined,
            tutorialPeriodStartInclusive: '2021-01-01',
            tutorialPeriodEndInclusive: '2021-02-01',
            useTutorialGroupChannels: true,
            usePublicTutorialGroupChannels: false,
            tutorialGroupFreePeriods: [],
        });
        req.flush(loaded);
        expect(result).toEqual(loaded);
    });

    it('should update a configuration, keeping its id and free periods', () => {
        service.update(1, 5, loaded, settings).subscribe();

        const req = httpMock.expectOne({ method: 'PUT', url: `${CONFIGURATIONS_URL}/5` });
        expect(req.request.body.id).toBe(5);
        expect(req.request.body.tutorialGroupFreePeriods).toEqual(loaded.tutorialGroupFreePeriods);
        expect(req.request.body.useTutorialGroupChannels).toBe(true);
        expect(req.request.body.usePublicTutorialGroupChannels).toBe(false);
        req.flush(loaded);
    });
});

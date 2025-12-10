import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { generateExampleTutorialGroupsConfiguration } from 'test/helpers/sample/tutorialgroup/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfigurationService } from 'app/tutorialgroup/shared/service/tutorial-groups-configuration.service';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { provideHttpClient } from '@angular/common/http';

describe('TutorialGroupsConfigurationService', () => {
    let service: TutorialGroupsConfigurationService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupSession;

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
    });

    it('getOneOfCourse', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault);
        service
            .getOneOfCourse(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { id: 0 });
        const expected = Object.assign({}, returnedFromService);
        service
            .create(new TutorialGroupsConfiguration(), 1, [])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = Object.assign({}, elemDefault, { location: 'Test' });
        const expected = Object.assign({}, returnedFromService);

        service
            .update(1, 1, new TutorialGroupsConfiguration(), [])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));
});

import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { take } from 'rxjs/operators';
import { TutorialGroupSession } from 'app/entities/tutorial-group/tutorial-group-session.model';
import { generateExampleTutorialGroupsConfiguration } from '../helpers/tutorialGroupsConfigurationExampleModels';
import { TutorialGroupsConfigurationService } from 'app/course/tutorial-groups/services/tutorial-groups-configuration.service';
import { TutorialGroupsConfiguration } from 'app/entities/tutorial-group/tutorial-groups-configuration.model';

describe('TutorialGroupsConfigurationService', () => {
    let service: TutorialGroupsConfigurationService;
    let httpMock: HttpTestingController;
    let elemDefault: TutorialGroupSession;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        service = TestBed.inject(TutorialGroupsConfigurationService);
        httpMock = TestBed.inject(HttpTestingController);

        elemDefault = generateExampleTutorialGroupsConfiguration({});
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('getOneOfCourse', fakeAsync(() => {
        const returnedFromService = { ...elemDefault };
        service
            .getOneOfCourse(1)
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(returnedFromService);
        tick();
    }));

    it('create', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, id: 0 };
        const expected = { ...returnedFromService };
        service
            .create(new TutorialGroupsConfiguration(), 1, [])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('update', fakeAsync(() => {
        const returnedFromService = { ...elemDefault, location: 'Test' };
        const expected = { ...returnedFromService };

        service
            .update(1, 1, new TutorialGroupsConfiguration(), [])
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));

        const req = httpMock.expectOne({ method: 'PUT' });
        req.flush(returnedFromService);
        tick();
    }));
});

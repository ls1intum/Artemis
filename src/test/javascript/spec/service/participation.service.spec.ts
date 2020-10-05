import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { map, take } from 'rxjs/operators';
import * as moment from 'moment';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Participation } from 'app/entities/participation/participation.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { expect } from '../helpers/jasmine.jest.fix';

describe('Participation Service', () => {
    let injector: TestBed;
    let service: ParticipationService;
    let httpMock: HttpTestingController;
    let elemDefault: Participation;
    let currentDate: moment.Moment;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
        });
        injector = getTestBed();
        service = injector.get(ParticipationService);
        httpMock = injector.get(HttpTestingController);
        currentDate = moment();

        elemDefault = new StudentParticipation();
    });

    describe('Service methods', async () => {
        it('should find an element', async () => {
            const returnedFromService = Object.assign(
                {
                    initializationDate: currentDate.toDate(),
                },
                elemDefault,
            );
            service
                .find(123)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: elemDefault }));

            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should update a Participation', async () => {
            const returnedFromService = Object.assign(
                {
                    repositoryUrl: 'BBBBBB',
                    buildPlanId: 'BBBBBB',
                    initializationState: 'BBBBBB',
                    initializationDate: currentDate.toDate(),
                    presentationScore: 1,
                },
                elemDefault,
            );

            const expected = Object.assign(
                {
                    initializationDate: currentDate,
                },
                returnedFromService,
            );
            service
                .update(expected)
                .pipe(take(1))
                .subscribe((resp) => expect(resp).toMatchObject({ body: expected }));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(JSON.stringify(returnedFromService));
        });

        it('should return a list of Participation', async () => {
            const returnedFromService = Object.assign(
                {
                    repositoryUrl: 'BBBBBB',
                    buildPlanId: 'BBBBBB',
                    initializationState: 'BBBBBB',
                    initializationDate: currentDate.toDate(),
                    presentationScore: 1,
                },
                elemDefault,
            );
            const expected = Object.assign(
                {
                    initializationDate: currentDate,
                },
                returnedFromService,
            );
            service
                .findAllParticipationsByExercise(1)
                .pipe(
                    take(1),
                    map((resp) => resp.body),
                )
                .subscribe((body) => expect(body).toContainEqual(expected));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(JSON.stringify([returnedFromService]));
            httpMock.verify();
        });

        it('should delete a Participation', async () => {
            service.delete(123).subscribe((resp) => expect(resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
        });
    });

    afterEach(() => {
        httpMock.verify();
    });
});

/* tslint:disable max-line-length */
import { TestBed, getTestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { take, map } from 'rxjs/operators';
import * as moment from 'moment';
import { DATE_TIME_FORMAT } from 'app/shared/constants/input.constants';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { IParticipation, Participation, InitializationState } from 'app/shared/model/participation.model';

describe('Service Tests', () => {
    describe('Participation Service', () => {
        let injector: TestBed;
        let service: ParticipationService;
        let httpMock: HttpTestingController;
        let elemDefault: IParticipation;
        let currentDate: moment.Moment;
        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [HttpClientTestingModule]
            });
            injector = getTestBed();
            service = injector.get(ParticipationService);
            httpMock = injector.get(HttpTestingController);
            currentDate = moment();

            elemDefault = new Participation(0, 'AAAAAAA', 'AAAAAAA', InitializationState.UNINITIALIZED, currentDate, 0);
        });

        describe('Service methods', async () => {
            it('should find an element', async () => {
                const returnedFromService = Object.assign(
                    {
                        initializationDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                service
                    .find(123)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: elemDefault }));

                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should create a Participation', async () => {
                const returnedFromService = Object.assign(
                    {
                        id: 0,
                        initializationDate: currentDate.format(DATE_TIME_FORMAT)
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        initializationDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .create(new Participation(null))
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'POST' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should update a Participation', async () => {
                const returnedFromService = Object.assign(
                    {
                        repositoryUrl: 'BBBBBB',
                        buildPlanId: 'BBBBBB',
                        initializationState: 'BBBBBB',
                        initializationDate: currentDate.format(DATE_TIME_FORMAT),
                        presentationScore: 1
                    },
                    elemDefault
                );

                const expected = Object.assign(
                    {
                        initializationDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .update(expected)
                    .pipe(take(1))
                    .subscribe(resp => expect(resp).toMatchObject({ body: expected }));
                const req = httpMock.expectOne({ method: 'PUT' });
                req.flush(JSON.stringify(returnedFromService));
            });

            it('should return a list of Participation', async () => {
                const returnedFromService = Object.assign(
                    {
                        repositoryUrl: 'BBBBBB',
                        buildPlanId: 'BBBBBB',
                        initializationState: 'BBBBBB',
                        initializationDate: currentDate.format(DATE_TIME_FORMAT),
                        presentationScore: 1
                    },
                    elemDefault
                );
                const expected = Object.assign(
                    {
                        initializationDate: currentDate
                    },
                    returnedFromService
                );
                service
                    .query(expected)
                    .pipe(
                        take(1),
                        map(resp => resp.body)
                    )
                    .subscribe(body => expect(body).toContainEqual(expected));
                const req = httpMock.expectOne({ method: 'GET' });
                req.flush(JSON.stringify([returnedFromService]));
                httpMock.verify();
            });

            it('should delete a Participation', async () => {
                const rxPromise = service.delete(123).subscribe(resp => expect(resp.ok));

                const req = httpMock.expectOne({ method: 'DELETE' });
                req.flush({ status: 200 });
            });
        });

        afterEach(() => {
            httpMock.verify();
        });
    });
});

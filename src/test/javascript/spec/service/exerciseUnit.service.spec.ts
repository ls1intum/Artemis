import { ExerciseUnitService } from 'app/lecture/lecture-unit/lecture-unit-management/exerciseUnit.service';
import { getTestBed, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { ExerciseUnit } from 'app/entities/lecture-unit/exerciseUnit.model';
import { ArtemisTestModule } from '../test.module';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { SessionStorageService } from 'ngx-webstorage';
import { TranslateService } from '@ngx-translate/core';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { take } from 'rxjs/operators';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exercise Unit Service', () => {
    let injector: TestBed;
    let service: ExerciseUnitService;
    let httpMock: HttpTestingController;
    let elemDefault: ExerciseUnit;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        injector = getTestBed();
        service = injector.get(ExerciseUnitService);
        httpMock = injector.get(HttpTestingController);

        elemDefault = new ExerciseUnit();
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should be created', () => {
        expect(service).to.be.ok;
    });

    it('should create an ExerciseUnit', async () => {
        const returnedFromService = Object.assign(
            {
                id: 0,
            },
            elemDefault,
        );
        service.create(new ExerciseUnit(), 0).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(JSON.stringify(returnedFromService));
    });

    it('should find all by Lecture Id', async () => {
        const returnedFromService = [
            Object.assign(
                {
                    id: 0,
                },
                elemDefault,
            ),
        ];
        service.findAllByLectureId(0).pipe(take(1)).subscribe();

        const req = httpMock.expectOne({ method: 'GET' });
        req.flush(JSON.stringify(returnedFromService));
    });
});

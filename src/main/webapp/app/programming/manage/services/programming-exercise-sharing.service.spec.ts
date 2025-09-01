import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgrammingExerciseSharingService } from 'app/programming/manage/services/programming-exercise-sharing.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SharingInfo, ShoppingBasket } from 'app/sharing/sharing.model';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

describe('ProgrammingExercise Sharing Service', () => {
    let service: ProgrammingExerciseSharingService;
    let httpMock: HttpTestingController;

    let defaultShoppingBasket: ShoppingBasket;
    const defailtSharingInfo: SharingInfo = {
        basketToken: '123',
        returnURL: 'http://localhost:9000',
        apiBaseURL: 'http://localhost:9000/api',
        checksum: 'checksum',
        selectedExercise: 123,
        isAvailable: () => true,
        clear: () => {},
        validate: () => {},
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseSharingService);
                httpMock = TestBed.inject(HttpTestingController);

                defaultShoppingBasket = { exerciseInfo: [], userInfo: { email: 'unused', name: 'unused' }, tokenValidUntil: dayjs().add(1, 'hour').toDate() } as ShoppingBasket;
            });
    });

    describe('Service methods', () => {
        it('should get an shared exercise', fakeAsync(() => {
            const returnedFromService = {
                ...defaultShoppingBasket,
            };
            service.getSharedExercises(defailtSharingInfo).subscribe((res: ShoppingBasket) => expect(res).toEqual(defaultShoppingBasket));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
        }));

        it('should get exercise details', fakeAsync(() => {
            const programmingExercise = new ProgrammingExercise(undefined, undefined);

            service
                .loadDetailsForExercises({
                    basketToken: '123',
                    returnURL: 'http://localhost:9000',
                    apiBaseURL: 'http://localhost:9000/api',
                    checksum: 'checksum',
                    selectedExercise: 123,
                    isAvailable: () => true,
                    clear: () => {},
                    validate: () => {},
                })
                .subscribe((res: ProgrammingExercise) => expect(res).toEqual(programmingExercise));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(programmingExercise);
            tick();
        }));

        it('should setup for import', fakeAsync(() => {
            const programmingExercise = new ProgrammingExercise(undefined, undefined);

            service
                .setUpFromSharingImport(programmingExercise, new Course(), defailtSharingInfo)
                .subscribe((res: HttpResponse<ProgrammingExercise>) => expect(res.body).toEqual(programmingExercise));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(programmingExercise);
            tick();
        }));

        it('should setup for import with null body response', fakeAsync(() => {
            const programmingExercise = new ProgrammingExercise(undefined, undefined);

            service
                .setUpFromSharingImport(programmingExercise, new Course(), defailtSharingInfo)
                .subscribe((res: HttpResponse<ProgrammingExercise>) => expect(res.body).toBeNull());
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(null);
            tick();
        }));
        it('should setup for import without template participation', fakeAsync(() => {
            const programmingExercise = new ProgrammingExercise(undefined, undefined);
            programmingExercise.templateParticipation = undefined;
            programmingExercise.solutionParticipation = undefined;

            service
                .setUpFromSharingImport(programmingExercise, new Course(), defailtSharingInfo)
                .subscribe((res: HttpResponse<ProgrammingExercise>) => expect(res.body).toEqual(programmingExercise));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(programmingExercise);
            tick();
        }));

        it('should setup for export programming exercise to sharing', fakeAsync(() => {
            const programmingExercise = new ProgrammingExercise(undefined, undefined);
            programmingExercise.templateParticipation = undefined;
            programmingExercise.solutionParticipation = undefined;

            service.exportProgrammingExerciseToSharing(5, 'http://localhost:9000').subscribe((res: HttpResponse<string>) => expect(res.body).toBe('some target URL'));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush('some target URL');
            tick();
        }));
    });

    afterEach(() => {
        httpMock.verify();
    });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { advanceTo } from 'jest-date-mock';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { AuditsComponent } from 'app/core/admin/audits/audits.component';
import { AuditsService } from 'app/core/admin/audits/audits.service';
import { Audit } from 'app/core/admin/audits/audit.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { DatePipe } from '@angular/common';

function build2DigitsDatePart(datePart: number) {
    return `0${datePart}`.slice(-2);
}

function getDate(isToday = true) {
    let date: Date = new Date();
    if (isToday) {
        // Today + 1 day - needed if the current day must be included
        date.setDate(date.getDate() + 1);
    } else {
        // get last month
        if (date.getMonth() === 0) {
            date = new Date(date.getFullYear() - 1, 11, date.getDate());
        } else {
            date = new Date(date.getFullYear(), date.getMonth() - 1, date.getDate());
        }
    }
    const monthString = build2DigitsDatePart(date.getMonth() + 1);
    const dateString = build2DigitsDatePart(date.getDate());
    return `${date.getFullYear()}-${monthString}-${dateString}`;
}

describe('AuditsComponent', () => {
    let comp: AuditsComponent;
    let fixture: ComponentFixture<AuditsComponent>;
    let service: AuditsService;
    let mockActivatedRoute: MockActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ courseId: 123 }),
                },
                {
                    provide: Router,
                    useClass: MockRouter,
                },
                { provide: TranslateService, useClass: MockTranslateService },
                DatePipe,
                provideHttpClient(),
            ],
        })
            .overrideTemplate(AuditsComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(AuditsComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(AuditsService);
                mockActivatedRoute = TestBed.inject(ActivatedRoute) as MockActivatedRoute;
                mockActivatedRoute.setParameters({
                    sort: 'id,desc',
                });
            });
    });

    describe('today function', () => {
        it('should set toDate to current date', () => {
            comp.ngOnInit();
            expect(comp.toDate).toBe(getDate());
        });

        it('if current day is last day of month then should set toDate to first day of next month', () => {
            advanceTo(new Date(2019, 0, 31, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.toDate).toBe('2019-02-01');
        });

        it('if current day is not last day of month then should set toDate to next day of current month', () => {
            advanceTo(new Date(2019, 0, 27, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.toDate).toBe('2019-01-28');
        });
    });

    describe('previousMonth function', () => {
        it('should set fromDate to previous month', () => {
            comp.ngOnInit();
            expect(comp.fromDate).toBe(getDate(false));
        });

        it('if current month is January then should set fromDate to previous year last month', () => {
            advanceTo(new Date(2019, 0, 20, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.fromDate).toBe('2018-12-20');
        });

        it('if current month is not January then should set fromDate to current year previous month', () => {
            advanceTo(new Date(2019, 1, 20, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.fromDate).toBe('2019-01-20');
        });
    });

    describe('By default, on init', () => {
        it('should set all default values correctly', () => {
            fixture.detectChanges();
            expect(comp.toDate).toBe(getDate());
            expect(comp.fromDate).toBe(getDate(false));
            expect(comp.itemsPerPage).toBe(ITEMS_PER_PAGE);
            expect(comp.page).toBe(1);
            expect(comp.ascending).toBeFalse();
            expect(comp.predicate).toBe('id');
        });
    });

    describe('onInit', () => {
        it('should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('X-Total-Count', '1');
            const audit = new Audit({ remoteAddress: '127.0.0.1', sessionId: '123' }, 'user', '20140101', 'AUTHENTICATION_SUCCESS');
            jest.spyOn(service, 'query').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [audit],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalledOnce();
            expect(comp.audits).toContainEqual(audit);
            expect(comp.totalItems).toBe(1);
        });
    });

    describe('Create sort object', () => {
        beforeEach(() => {
            jest.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
        });

        it('should sort only by id asc', () => {
            // GIVEN
            mockActivatedRoute.setParameters({
                sort: 'id,desc',
            });

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalledWith(
                expect.objectContaining({
                    sort: ['id,desc'],
                }),
            );
        });

        it('should sort by timestamp asc then by id', () => {
            // GIVEN
            mockActivatedRoute.setParameters({
                sort: 'timestamp,asc',
            });

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalledWith(
                expect.objectContaining({
                    sort: ['timestamp,asc', 'id'],
                }),
            );
        });
    });
});

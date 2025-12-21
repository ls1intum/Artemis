/**
 * Vitest tests for AuditsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { DatePipe } from '@angular/common';

import { AuditsComponent } from 'app/core/admin/audits/audits.component';
import { AuditsService } from 'app/core/admin/audits/audits.service';
import { Audit } from 'app/core/admin/audits/audit.model';
import { ITEMS_PER_PAGE } from 'app/shared/constants/pagination.constants';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';

function build2DigitsDatePart(datePart: number) {
    return `0${datePart}`.slice(-2);
}

function getDate(isToday = true) {
    let date: Date = new Date();
    if (isToday) {
        date.setDate(date.getDate() + 1);
    } else {
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
    setupTestBed({ zoneless: true });

    let comp: AuditsComponent;
    let fixture: ComponentFixture<AuditsComponent>;
    let service: AuditsService;
    let mockActivatedRoute: MockActivatedRoute;

    beforeEach(async () => {
        const mockRouter = {
            navigate: vi.fn().mockReturnValue(Promise.resolve(true)),
        };

        await TestBed.configureTestingModule({
            imports: [AuditsComponent],
            providers: [{ provide: ActivatedRoute, useValue: new MockActivatedRoute({ courseId: 123 }) }, { provide: Router, useValue: mockRouter }, DatePipe, provideHttpClient()],
        })
            .overrideTemplate(AuditsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(AuditsComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(AuditsService);
        mockActivatedRoute = TestBed.inject(ActivatedRoute) as unknown as MockActivatedRoute;
        mockActivatedRoute.setParameters({ sort: 'id,desc' });
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    describe('today function', () => {
        it('should set toDate to current date plus one day', () => {
            comp.ngOnInit();
            expect(comp.toDate()).toBe(getDate());
        });

        it('if current day is last day of month then should set toDate to first day of next month', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date(2019, 0, 31, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.toDate()).toBe('2019-02-01');
        });

        it('if current day is not last day of month then should set toDate to next day of current month', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date(2019, 0, 27, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.toDate()).toBe('2019-01-28');
        });
    });

    describe('previousMonth function', () => {
        it('should set fromDate to previous month', () => {
            comp.ngOnInit();
            expect(comp.fromDate()).toBe(getDate(false));
        });

        it('if current month is January then should set fromDate to previous year last month', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date(2019, 0, 20, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.fromDate()).toBe('2018-12-20');
        });

        it('if current month is not January then should set fromDate to current year previous month', () => {
            vi.useFakeTimers();
            vi.setSystemTime(new Date(2019, 1, 20, 0, 0, 0));
            comp.ngOnInit();
            expect(comp.fromDate()).toBe('2019-01-20');
        });
    });

    describe('By default, on init', () => {
        it('should set all default values correctly', () => {
            fixture.detectChanges();
            expect(comp.toDate()).toBe(getDate());
            expect(comp.fromDate()).toBe(getDate(false));
            expect(comp.itemsPerPage).toBe(ITEMS_PER_PAGE);
            expect(comp.page()).toBe(1);
            expect(comp.ascending()).toBe(false);
            expect(comp.predicate()).toBe('id');
        });
    });

    describe('onInit', () => {
        it('should call load all on init', () => {
            const headers = new HttpHeaders().append('X-Total-Count', '1');
            const audit = new Audit({ remoteAddress: '127.0.0.1', sessionId: '123' }, 'user', '20140101', 'AUTHENTICATION_SUCCESS');
            vi.spyOn(service, 'query').mockReturnValue(
                of(
                    new HttpResponse({
                        body: [audit],
                        headers,
                    }),
                ),
            );

            comp.ngOnInit();

            expect(service.query).toHaveBeenCalledOnce();
            expect(comp.audits()).toContainEqual(audit);
            expect(comp.totalItems()).toBe(1);
        });
    });

    describe('Create sort object', () => {
        beforeEach(() => {
            vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
        });

        it('should sort only by id desc', () => {
            mockActivatedRoute.setParameters({ sort: 'id,desc' });

            comp.ngOnInit();

            expect(service.query).toHaveBeenCalledWith(
                expect.objectContaining({
                    sort: ['id,desc'],
                }),
            );
        });

        it('should sort by timestamp asc then by id', () => {
            mockActivatedRoute.setParameters({ sort: 'timestamp,asc' });

            comp.ngOnInit();

            expect(service.query).toHaveBeenCalledWith(
                expect.objectContaining({
                    sort: ['timestamp,asc', 'id'],
                }),
            );
        });
    });
});

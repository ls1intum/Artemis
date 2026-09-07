/**
 * Vitest tests for AuditsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { DatePipe } from '@angular/common';

import { AuditsComponent } from 'app/admin/audits/audits.component';
import { AuditsService } from 'app/admin/audits/audits.service';
import { Audit } from 'app/admin/audits/audit.model';
import { ITEMS_PER_PAGE } from 'app/foundation/constants/pagination.constants';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { AuditLogType } from 'app/admin/audits/audit-log-type.model';

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
        // Prevent real HTTP calls in tests that call ngOnInit() without their own service mock
        vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
        mockActivatedRoute = TestBed.inject(ActivatedRoute) as unknown as MockActivatedRoute;
        mockActivatedRoute.setParameters({ sort: 'id,desc' });
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.restoreAllMocks();
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

    describe('picker value round-trip', () => {
        // The picker emits a Date/dayjs; the audits filter stores yyyy-MM-dd strings in the URL. The conversion
        // must use dayjs format tokens (YYYY-MM-DD), not Angular DatePipe tokens, or the URL value is malformed
        // (e.g. "yyyy-06-We") and the picker can never round-trip it back.
        it('stores a picker date as a yyyy-MM-dd string', () => {
            comp.updateFromDate(new Date(2026, 5, 17, 0, 0, 0));
            expect(comp.fromDate()).toBe('2026-06-17');
            comp.updateToDate(new Date(2026, 5, 20, 0, 0, 0));
            expect(comp.toDate()).toBe('2026-06-20');
        });

        it('clears the filter for an invalid picker value instead of keeping the previous date', () => {
            comp.updateFromDate(new Date(2026, 5, 17, 0, 0, 0));

            // The template passes undefined when fromPicker.isValid() is false (an invalid manual entry the picker
            // keeps visible via keepInvalid). The previous valid date must not be written back.
            comp.updateFromDate(undefined);

            expect(comp.fromDate()).toBe('');
            expect(comp.hasDateRange()).toBe(false);
        });

        it('should request every audit once a date is cleared, rather than showing nothing', () => {
            const headers = new HttpHeaders().append('X-Total-Count', '42');
            const audit = new Audit({ remoteAddress: '127.0.0.1', sessionId: '123' }, 'user', '20140101', 'AUTHENTICATION_SUCCESS');
            const query = vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse({ body: [audit], headers })));
            comp.ngOnInit();

            expect(query).toHaveBeenLastCalledWith(expect.objectContaining({ fromDate: getDate(false), toDate: getDate() }));

            // Clearing a date is a request to stop filtering, so the dates must be dropped from the query: the
            // filtered endpoint is only matched when both are present, and the unfiltered one returns all audits.
            query.mockClear();
            comp.updateFromDate(undefined);
            // The router is mocked here, so a navigation cannot feed new query params back in; drive the reload the
            // navigation would have caused.
            comp['loadData']();

            expect(comp.hasDateRange()).toBe(false);
            const params = query.mock.calls.at(-1)![0];
            expect(params).not.toHaveProperty('fromDate');
            expect(params).not.toHaveProperty('toDate');
            expect(comp.audits()).toHaveLength(1);
        });

        it('should show all audits when reloading a URL that carries only one date', () => {
            const query = vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
            // What clearing the from picker leaves behind: `transition` drops the empty half from the URL.
            mockActivatedRoute.setParameters({ sort: 'id,desc', to: '2026-06-20' });

            comp.ngOnInit();

            // The seeded default must not step in for the missing half and revive the filter the user cleared.
            expect(comp.fromDate()).toBe('');
            expect(comp.toDate()).toBe('2026-06-20');
            expect(comp.hasDateRange()).toBe(false);
            const params = query.mock.calls.at(-1)![0];
            expect(params).not.toHaveProperty('fromDate');
            expect(params).not.toHaveProperty('toDate');
        });

        it('should show all audits when reloading a URL that carries only the from date', () => {
            const query = vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
            mockActivatedRoute.setParameters({ sort: 'id,desc', from: '2026-06-17' });

            comp.ngOnInit();

            expect(comp.fromDate()).toBe('2026-06-17');
            expect(comp.toDate()).toBe('');
            expect(comp.hasDateRange()).toBe(false);
            const params = query.mock.calls.at(-1)![0];
            expect(params).not.toHaveProperty('fromDate');
            expect(params).not.toHaveProperty('toDate');
        });

        it('should keep the default month range when the URL carries no date at all', () => {
            const query = vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
            mockActivatedRoute.setParameters({ sort: 'id,desc' });

            comp.ngOnInit();

            expect(comp.hasDateRange()).toBe(true);
            expect(query).toHaveBeenLastCalledWith(expect.objectContaining({ fromDate: getDate(false), toDate: getDate() }));
        });

        it('should leave a cleared date out of the URL instead of writing an empty parameter', () => {
            vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
            const navigate = TestBed.inject(Router).navigate as unknown as ReturnType<typeof vi.fn>;
            comp.ngOnInit();
            navigate.mockClear();

            comp.updateToDate(undefined);
            comp.transition();

            const queryParams = navigate.mock.calls.at(-1)![1]!.queryParams!;
            expect(queryParams['to']).toBeUndefined();
            expect(queryParams['from']).toBe(getDate(false));
        });
    });

    describe('By default, on init', () => {
        it('should set all default values correctly', () => {
            vi.spyOn(service, 'query').mockReturnValue(of(new HttpResponse<Audit[]>()));
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

    describe('pagination (tum-ui paginator)', () => {
        it('converts the 0-indexed paginator event to the 1-indexed page and navigates', () => {
            comp.ngOnInit(); // sets the default date range
            const router = TestBed.inject(Router);
            (router.navigate as unknown as ReturnType<typeof vi.fn>).mockClear();

            comp.onPageChange(2);

            expect(comp.page()).toBe(3);
            expect(router.navigate).toHaveBeenCalledWith(['/admin/audits'], expect.objectContaining({ queryParams: expect.objectContaining({ page: 3 }) }));
        });

        it('still pages through the unfiltered results when a date has been cleared', () => {
            comp.ngOnInit();
            comp.fromDate.set('');
            const router = TestBed.inject(Router);
            (router.navigate as unknown as ReturnType<typeof vi.fn>).mockClear();

            comp.onPageChange(4);

            // Clearing a date shows every audit, which can span many pages, so the paginator has to keep working.
            expect(comp.page()).toBe(5);
            expect(router.navigate).toHaveBeenCalledWith(['/admin/audits'], expect.objectContaining({ queryParams: expect.objectContaining({ page: 5 }) }));
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

    describe('extra data column', () => {
        it('shows every recorded payload entry, not just the two keys the login events use', () => {
            // A domain action records its own keys ("course=..." becomes { course: ... }) and an account security event
            // records e.g. a reason, so a column limited to message/remoteAddress would be empty for almost every row
            // in the Application and Security tabs.
            comp.audits.set([
                { data: { course: 'Intro to Programming' }, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'DELETE_COURSE' },
                { data: { reason: 'unknown-identifier' }, principal: 'anonymous', timestamp: '2026-08-21T10:01:00Z', type: 'PASSWORD_RESET_REQUEST_REJECTED' },
            ]);

            expect(comp.auditRows().map((row) => row.otherData)).toEqual([[{ key: 'course', value: 'Intro to Programming' }], [{ key: 'reason', value: 'unknown-identifier' }]]);
        });

        it('leaves out the keys that have their own rendering, so they are not shown twice', () => {
            comp.audits.set([{ data: { message: 'a message', remoteAddress: '127.0.0.1', course: 'kept' }, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'X' }]);

            expect(comp.auditRows()[0].otherData).toEqual([{ key: 'course', value: 'kept' }]);
        });

        it('never puts the session id on screen: it identifies a session rather than describing the event', () => {
            comp.audits.set([{ data: { sessionId: 'A1B2C3SESSIONTOKEN', course: 'kept' }, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'X' }]);

            expect(comp.auditRows()[0].otherData).toEqual([{ key: 'course', value: 'kept' }]);
        });

        it("keeps the login record's user agent, which the cell clips to one line", () => {
            // Every successful login carries one; dropping it would hide real audit data, so the template truncates
            // instead. The whole value stays in the row's title attribute.
            const userAgent = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36';
            comp.audits.set([{ data: { userAgent }, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'AUTHENTICATION_SUCCESS' }]);

            expect(comp.auditRows()[0].otherData).toEqual([{ key: 'userAgent', value: userAgent }]);
        });

        it('leaves out empty values rather than rendering a dangling label', () => {
            comp.audits.set([{ data: { course: '', reason: undefined, missing: null as never, kept: 'yes' }, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'X' }]);

            expect(comp.auditRows()[0].otherData).toEqual([{ key: 'kept', value: 'yes' }]);
        });

        it('tolerates an event with no payload at all', () => {
            comp.audits.set([{ data: undefined as never, principal: 'admin', timestamp: '2026-08-21T10:00:00Z', type: 'X' }]);

            expect(comp.auditRows()[0].otherData).toEqual([]);
        });
    });

    describe('audit log tabs', () => {
        it('offers exactly the three audit logs as tabs, in a stable order', () => {
            expect(comp.logTypeTabs.map((tab) => tab.value)).toEqual([AuditLogType.GENERAL, AuditLogType.SECURITY, AuditLogType.APPLICATION]);
        });

        it('defaults to the general log and sends it with the query', () => {
            comp.ngOnInit();

            expect(comp.logType()).toBe(AuditLogType.GENERAL);
            expect(service.query).toHaveBeenCalledWith(expect.objectContaining({ logType: AuditLogType.GENERAL }));
        });

        it('navigates with the selected log when a different tab is chosen, so the reload picks it up from the URL', () => {
            // Switching tabs goes through the router: transition() navigates, and the queryParamMap subscription then
            // reloads. The router is mocked here, so the observable contract to assert on is the navigation itself.
            comp.ngOnInit();
            const router = TestBed.inject(Router);

            comp.onLogTypeChange(AuditLogType.SECURITY);

            expect(comp.logType()).toBe(AuditLogType.SECURITY);
            expect(router.navigate).toHaveBeenCalledWith(['/admin/audits'], expect.objectContaining({ queryParams: expect.objectContaining({ logType: AuditLogType.SECURITY }) }));
        });

        it('resets to the first page when switching tabs, because page numbers do not carry over between logs', () => {
            comp.ngOnInit();
            comp.updatePage(4);

            comp.onLogTypeChange(AuditLogType.APPLICATION);

            expect(comp.page()).toBe(1);
        });

        it('ignores a re-selection of the tab that is already active', () => {
            comp.ngOnInit();
            const callsBefore = vi.mocked(service.query).mock.calls.length;

            comp.onLogTypeChange(AuditLogType.GENERAL);

            expect(vi.mocked(service.query).mock.calls).toHaveLength(callsBefore);
        });

        it('restores the log from the URL so a tab can be bookmarked', () => {
            mockActivatedRoute.setParameters({ sort: 'id,desc', logType: AuditLogType.APPLICATION });

            comp.ngOnInit();

            expect(comp.logType()).toBe(AuditLogType.APPLICATION);
            expect(service.query).toHaveBeenCalledWith(expect.objectContaining({ logType: AuditLogType.APPLICATION }));
        });

        it('falls back to the general log for an unknown value in a hand-edited URL', () => {
            mockActivatedRoute.setParameters({ sort: 'id,desc', logType: 'NOT_A_LOG' });

            comp.ngOnInit();

            expect(comp.logType()).toBe(AuditLogType.GENERAL);
        });

        it('returns to the general log when a later navigation drops the parameter', () => {
            // Angular reuses the component across query-parameter-only navigations, so a stale signal would keep
            // querying the previously selected log even though the URL no longer names one.
            mockActivatedRoute.setParameters({ sort: 'id,desc', logType: AuditLogType.SECURITY });
            comp.ngOnInit();
            expect(comp.logType()).toBe(AuditLogType.SECURITY);

            mockActivatedRoute.setParameters({ sort: 'id,desc' });

            expect(comp.logType()).toBe(AuditLogType.GENERAL);
            expect(service.query).toHaveBeenLastCalledWith(expect.objectContaining({ logType: AuditLogType.GENERAL }));
        });

        it('returns to the general log when a later navigation carries an unknown value', () => {
            mockActivatedRoute.setParameters({ sort: 'id,desc', logType: AuditLogType.APPLICATION });
            comp.ngOnInit();
            expect(comp.logType()).toBe(AuditLogType.APPLICATION);

            mockActivatedRoute.setParameters({ sort: 'id,desc', logType: 'NOT_A_LOG' });

            expect(comp.logType()).toBe(AuditLogType.GENERAL);
        });
    });
});

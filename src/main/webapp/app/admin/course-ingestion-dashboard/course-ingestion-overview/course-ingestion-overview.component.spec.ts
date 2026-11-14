import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionOverviewComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-overview/course-ingestion-overview.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview } from 'app/openapi/model/index-overview';

describe('CourseIngestionOverviewComponent', () => {
    let component: CourseIngestionOverviewComponent;
    let fixture: ComponentFixture<CourseIngestionOverviewComponent>;
    let service: CourseIngestionDashboardService;

    const reachableOverview: IndexOverview = {
        weaviateReachable: true,
        weaviateAddress: 'http://weaviate:8080',
        irisEnabled: true,
        irisReachable: true,
        collections: [
            { collection: 'ArtemisSearchableEntity', count: 42, readable: true },
            { collection: 'LectureUnits', readable: false },
            { collection: 'Lectures', count: 0, readable: true },
        ],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionOverviewComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(of(reachableOverview));

        fixture = TestBed.createComponent(CourseIngestionOverviewComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load the overview on init', async () => {
        expect(service.getIndexOverview).not.toHaveBeenCalled();
        expect(component.overview()).toBeUndefined();
        expect(component.loading()).toBe(true);
        expect(component.error()).toBe(false);
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.overview()).toEqual(reachableOverview);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
    });

    it('should render the Weaviate address and per-collection counts', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const element: HTMLElement = fixture.nativeElement;
        expect(element.querySelector('[data-testid="weaviate-status"]')).toBeTruthy();
        expect(element.querySelector('[data-testid="weaviate-address"]')?.textContent).toContain('http://weaviate:8080');

        const rows = element.querySelectorAll('[data-testid="collections-table"] tbody tr');
        expect(rows).toHaveLength(3);
        // Readable collection shows its numeric count.
        expect(rows[0].textContent).toContain('42');
        expect(rows[1].querySelector('[jhiTranslate="artemisApp.courseIngestionDashboard.collections.unavailable"]')).toBeTruthy();
        expect(rows[1].querySelector('td:last-child')?.textContent).not.toContain('0');
        expect(rows[2].querySelector('td:last-child')?.textContent?.trim()).toBe('0');
        expect(rows[2].querySelector('[jhiTranslate="artemisApp.courseIngestionDashboard.collections.unavailable"]')).toBeNull();
    });

    it('should render an unreachable status when Weaviate is down', async () => {
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(of({ ...reachableOverview, weaviateReachable: false }));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        const status = fixture.nativeElement.querySelector('[data-testid="weaviate-status"]');
        expect(status).toBeTruthy();
        expect(status.getAttribute('data-severity')).toBe('error');
    });

    it('should set the error signal when the overview fails to load', async () => {
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(throwError(() => new Error('boom')));
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.error()).toBe(true);
        expect(component.loading()).toBe(false);
        expect(component.overview()).toBeUndefined();
    });

    it('should keep the newest overview when an older reload answers or fails last', async () => {
        const first = new Subject<IndexOverview>();
        const second = new Subject<IndexOverview>();
        vi.mocked(service.getIndexOverview).mockReturnValueOnce(first.asObservable()).mockReturnValueOnce(second.asObservable());

        component.reload();
        expect(first.observed).toBe(true);
        component.reload();
        expect(first.observed).toBe(false);
        expect(second.observed).toBe(true);
        const newer: IndexOverview = { ...reachableOverview, collections: [{ collection: 'ArtemisSearchableEntity', count: 43, readable: true }] };
        second.next(newer);
        second.complete();
        first.next(reachableOverview);
        first.error(new Error('stale failure'));

        expect(first.observed).toBe(false);
        expect(component.overview()).toEqual(newer);
        expect(component.error()).toBe(false);
        expect(component.loading()).toBe(false);
    });

    it('should retain data while reloading, clear it on error, and recover on an explicit reload', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        const failed = new Subject<IndexOverview>();
        const recovered = new Subject<IndexOverview>();
        vi.mocked(service.getIndexOverview).mockReturnValueOnce(failed).mockReturnValueOnce(recovered);

        component.reload();
        expect(component.loading()).toBe(true);
        expect(component.error()).toBe(false);
        expect(component.overview()).toEqual(reachableOverview);
        failed.error(new Error('unavailable'));
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(true);
        expect(component.overview()).toBeUndefined();
        expect(service.getIndexOverview).toHaveBeenCalledTimes(2);

        component.reload();
        expect(component.loading()).toBe(true);
        expect(component.error()).toBe(false);
        recovered.next(reachableOverview);
        recovered.complete();
        expect(component.overview()).toEqual(reachableOverview);
        expect(component.loading()).toBe(false);
        expect(component.error()).toBe(false);
        expect(service.getIndexOverview).toHaveBeenCalledTimes(3);
    });

    it('should unsubscribe the pending request when destroyed', () => {
        const pending = new Subject<IndexOverview>();
        vi.mocked(service.getIndexOverview).mockReturnValueOnce(pending);
        fixture.detectChanges();
        expect(pending.observed).toBe(true);

        fixture.destroy();
        expect(pending.observed).toBe(false);
        pending.next(reachableOverview);
        expect(component.overview()).toBeUndefined();
    });

    it('should report Iris as reachable when it answers', async () => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.querySelector('[data-testid="iris-status"]')?.getAttribute('data-severity')).toBe('success');
    });

    it('should report Iris as unreachable when the module is on but it does not answer', async () => {
        // Enabled is configuration; reachable is the question an admin is actually asking.
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(of({ ...reachableOverview, irisEnabled: true, irisReachable: false }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.querySelector('[data-testid="iris-status"]')?.getAttribute('data-severity')).toBe('error');
    });

    it('should report Iris as disabled rather than unreachable when the module is off', async () => {
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(of({ ...reachableOverview, irisEnabled: false, irisReachable: false }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(document.querySelector('[data-testid="iris-status"]')?.getAttribute('data-severity')).toBe('disabled');
    });
});

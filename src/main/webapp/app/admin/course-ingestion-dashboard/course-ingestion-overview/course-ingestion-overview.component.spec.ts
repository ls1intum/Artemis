import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionOverviewComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-overview/course-ingestion-overview.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionOverviewComponent', () => {
    let component: CourseIngestionOverviewComponent;
    let fixture: ComponentFixture<CourseIngestionOverviewComponent>;
    let service: CourseIngestionDashboardService;

    const reachableOverview: IndexOverview = {
        weaviateReachable: true,
        weaviateAddress: 'http://weaviate:8080',
        irisEnabled: true,
        collections: [
            { collection: 'ArtemisSearchableEntity', count: 42, readable: true },
            { collection: 'LectureUnits', count: null, readable: false },
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
        expect(rows).toHaveLength(2);
        // Readable collection shows its numeric count.
        expect(rows[0].textContent).toContain('42');
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
});

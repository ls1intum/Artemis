import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionDashboardComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { IndexOverview } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionDashboardComponent', () => {
    let component: CourseIngestionDashboardComponent;
    let fixture: ComponentFixture<CourseIngestionDashboardComponent>;
    let service: CourseIngestionDashboardService;

    const overview: IndexOverview = {
        weaviateReachable: true,
        weaviateAddress: 'http://weaviate:8080',
        irisEnabled: true,
        irisReachable: true,
        collections: [{ collection: 'ArtemisSearchableEntity', count: 42, readable: true }],
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionDashboardComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getIndexOverview').mockReturnValue(of(overview));
        vi.spyOn(service, 'getLiveCoveragePage').mockReturnValue(of({ content: [], totalElements: 0 }));
        vi.spyOn(service, 'getStoredCoverage').mockReturnValue(of({ content: [], totalElements: 0 }));

        fixture = TestBed.createComponent(CourseIngestionDashboardComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create and render the overview and coverage table', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-course-ingestion-overview')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-course-ingestion-coverage-table')).toBeTruthy();
    });
});

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionBrowserDetailComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser-detail/course-ingestion-browser-detail.component';
import { CourseIngestionDashboardService } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.service';
import { CourseBrowserData, IndexedEntityRecord, IngestionTypeCount } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionBrowserDetailComponent', () => {
    let component: CourseIngestionBrowserDetailComponent;
    let fixture: ComponentFixture<CourseIngestionBrowserDetailComponent>;
    let service: CourseIngestionDashboardService;

    const data: CourseBrowserData = {
        entities: [
            { type: 'lecture', entityId: 20, title: 'Week 1', ingestedAt: '2026-08-26T09:00:00Z' },
            { type: 'lecture_unit', entityId: 11, title: 'Intro slides', lectureId: 20, ingestedAt: '2026-08-26T09:00:00Z' },
        ],
        contentPresence: [{ key: 'slides', unitIds: [11] }],
        missingEntities: [{ type: 'exercise', entityId: 5, title: 'Sorting' }],
        contentGaps: [],
    };

    const typeCounts: IngestionTypeCount[] = [{ type: 'exercise', expected: 3, indexed: 2, missing: 1, orphaned: 0 }];

    const records: IndexedEntityRecord[] = [
        { type: 'lecture', entityId: 20, title: 'Week 1', ingestedAt: '2026-08-26T09:00:00Z', properties: { title: 'Week 1', description: 'Body text' } },
    ];

    const query = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);

    const settle = async (): Promise<void> => {
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionBrowserDetailComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), provideTranslateService()],
        });
        service = TestBed.inject(CourseIngestionDashboardService);
        vi.spyOn(service, 'getIndexedEntityRecords').mockReturnValue(of(records));
        vi.spyOn(service, 'getUnitContent').mockReturnValue(of([{ ingestedAt: '2026-08-26T09:00:00Z', properties: { page_number: 3, page_text_content: 'Hello' } }]));

        fixture = TestBed.createComponent(CourseIngestionBrowserDetailComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', 7);
        fixture.componentRef.setInput('data', data);
        fixture.componentRef.setInput('typeCounts', typeCounts);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should prompt for a selection before anything is chosen', async () => {
        await settle();

        expect(query('detail-type')).toBeFalsy();
        expect(query('detail-collection')).toBeFalsy();
    });

    it('should show a type with its counts and name what is missing', async () => {
        component.selection.set({ kind: 'type', type: 'exercise' });
        await settle();

        expect(query('detail-type')).toBeTruthy();
        expect(query('detail-missing-list')?.textContent).toContain('Sorting');
    });

    it('should read the stored records for the selected type', async () => {
        const spy = vi.spyOn(service, 'getIndexedEntityRecords').mockReturnValue(of(records));
        component.selection.set({ kind: 'type', type: 'lecture' });
        await settle();

        expect(spy).toHaveBeenCalledWith(7, 'lecture');

        // A record's fields are behind its own disclosure, so nothing is shown until the row is opened.
        expect(query('stored-fields')).toBeFalsy();
        (query('detail-record-20') as HTMLButtonElement).click();
        fixture.detectChanges();

        expect(query('stored-fields')).toBeTruthy();
    });

    it('should show the stored record of a selected unit', async () => {
        vi.spyOn(service, 'getIndexedEntityRecords').mockReturnValue(
            of([{ type: 'lecture_unit', entityId: 11, title: 'Intro slides', ingestedAt: '2026-08-26T09:00:00Z', properties: { name: 'Intro slides' } }]),
        );
        component.selection.set({ kind: 'unit', unitId: 11 });
        await settle();

        expect(query('detail-unit')).toBeTruthy();
        expect(component.selectedRecord()?.entityId).toBe(11);
        // The unit holds slides, so its content chip is shown.
        expect(component.unitContentKeys()).toEqual(['slides']);
    });

    it('should fetch the objects of a collection only once it is selected', async () => {
        const spy = vi.spyOn(service, 'getUnitContent').mockReturnValue(of([{ ingestedAt: '2026-08-26T09:00:00Z', properties: { page_number: 3 } }]));

        component.selection.set({ kind: 'unit', unitId: 11 });
        await settle();
        expect(spy).not.toHaveBeenCalled();

        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        await settle();

        expect(spy).toHaveBeenCalledWith(7, 11, 'slides');
        expect(query('detail-collection')).toBeTruthy();
    });

    it('should label a content object by its page number', async () => {
        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        await settle();

        expect(component.labelledContent()[0].label).toBe('Page 3');
    });

    it('should fall back to a position label when an object has neither page nor segment time', async () => {
        vi.spyOn(service, 'getUnitContent').mockReturnValue(of([{ properties: { text: 'no position markers' } }]));
        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        await settle();

        expect(component.labelledContent()[0].label).toBe('#1');
    });

    it('should offer a breadcrumb back up from a collection', async () => {
        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        await settle();

        // Lecture then unit: the path the selection sits inside.
        expect(component.breadcrumbs().map((crumb) => crumb.label)).toEqual(['Week 1', 'Intro slides']);

        component.select(component.breadcrumbs()[0].selection);
        expect(component.selection()).toEqual({ kind: 'lecture', lectureId: 20 });
    });

    it('should page long content lists rather than rendering every chunk', async () => {
        // A slide deck runs to hundreds of chunks; rendering them all is what made the pane an endless scroll.
        const many = Array.from({ length: 60 }, (_, index) => ({ properties: { page_number: index + 1 } }));
        vi.spyOn(service, 'getUnitContent').mockReturnValue(of(many));

        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        await settle();

        expect(component.labelledContent()).toHaveLength(60);
        expect(component.pagedContent()).toHaveLength(25);
        expect(component.pagedContent()[0].label).toBe('Page 1');

        component.contentPage.set(1);
        fixture.detectChanges();

        expect(component.pagedContent()[0].label).toBe('Page 26');
    });

    it('should surface an error rather than an empty pane when a read fails', async () => {
        vi.spyOn(service, 'getIndexedEntityRecords').mockReturnValue(throwError(() => new Error('boom')));
        component.selection.set({ kind: 'type', type: 'lecture' });
        await settle();

        expect(component.error()).toBe(true);
        expect(query('detail-error')).toBeTruthy();
    });
});

import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';

import { CourseIngestionBrowserTreeComponent } from 'app/admin/course-ingestion-dashboard/course-ingestion-browser-tree/course-ingestion-browser-tree.component';
import { IndexedContentPresence, IndexedEntity, IngestionTypeCount } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

describe('CourseIngestionBrowserTreeComponent', () => {
    let component: CourseIngestionBrowserTreeComponent;
    let fixture: ComponentFixture<CourseIngestionBrowserTreeComponent>;

    const entity = (type: string, entityId: number, title: string, properties: Record<string, unknown> = {}): IndexedEntity => ({
        type,
        entityId,
        title,
        ingestedAt: '2026-08-26T09:00:00Z',
        properties: { type, entity_id: entityId, ...properties },
    });

    // Lecture 20 holds units 10 and 11. Unit 12 belongs to lecture 21, which is itself NOT indexed.
    const entities: IndexedEntity[] = [
        entity('lecture', 20, 'Week 1'),
        entity('lecture_unit', 11, 'Intro slides', { lecture_id: 20 }),
        entity('lecture_unit', 10, 'A recap', { lecture_id: 20 }),
        entity('lecture_unit', 12, 'Orphaned unit', { lecture_id: 21 }),
        entity('exercise', 1, 'Sorting'),
    ];

    const contentPresence: IndexedContentPresence[] = [
        { key: 'slides', unitIds: [11] },
        { key: 'transcript', unitIds: [11, 12] },
    ];

    const typeCounts: IngestionTypeCount[] = [
        { type: 'exercise', expected: 2, indexed: 1, missing: 1, orphaned: 0 },
        { type: 'lecture', expected: 1, indexed: 1, missing: 0, orphaned: 0 },
    ];

    const query = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    const click = (testId: string): void => {
        const element = query(testId);
        expect(element).toBeTruthy();
        (element as HTMLButtonElement).click();
        fixture.detectChanges();
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [CourseIngestionBrowserTreeComponent],
            providers: [provideTranslateService()],
        });
        fixture = TestBed.createComponent(CourseIngestionBrowserTreeComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('entities', entities);
        fixture.componentRef.setInput('contentPresence', contentPresence);
        fixture.componentRef.setInput('typeCounts', typeCounts);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should list every measured type in fixed order, including types the course has none of', () => {
        // Exams, FAQs, channels and the course itself have no counts here, but the scoreboard still lists them.
        ['exercise', 'lecture', 'lecture_unit', 'exam', 'faq', 'channel', 'course'].forEach((type) => {
            expect(query(`tree-node-type:${type}`)).toBeTruthy();
        });
    });

    it('should build the lecture tree from the loaded payloads', () => {
        expect(query('tree-node-lecture:20')).toBeTruthy();
        // Units are nested, so they are not rendered until their lecture is expanded.
        expect(query('tree-node-unit:11')).toBeFalsy();

        click('tree-toggle-lecture:20');

        expect(query('tree-node-unit:10')).toBeTruthy();
        expect(query('tree-node-unit:11')).toBeTruthy();
    });

    it('should keep a unit whose lecture is not indexed, and mark that lecture', () => {
        // Dropping the unit would hide exactly the gap this tool exists to surface.
        expect(query('tree-node-lecture:21')).toBeTruthy();
        expect(query('lecture-not-indexed')).toBeTruthy();

        click('tree-toggle-lecture:21');

        expect(query('tree-node-unit:12')).toBeTruthy();
    });

    it('should give a unit a node only for the collections that actually hold content for it', () => {
        click('tree-toggle-lecture:20');
        click('tree-toggle-unit:11');

        expect(query('tree-node-coll:11:slides')).toBeTruthy();
        expect(query('tree-node-coll:11:transcript')).toBeTruthy();
        expect(query('tree-node-coll:11:unit_summary')).toBeFalsy();

        // Unit 10 holds nothing, so it has no collections to open at all.
        click('tree-toggle-unit:10');
        expect(query('tree-node-coll:10:slides')).toBeFalsy();
    });

    it('should select a type when its scoreboard row is chosen', () => {
        click('tree-node-type:exercise');

        expect(component.selection()).toEqual({ kind: 'type', type: 'exercise' });
    });

    it('should expand a lecture when it is selected, since that is what opening one is for', () => {
        click('tree-node-lecture:20');

        expect(component.selection()).toEqual({ kind: 'lecture', lectureId: 20 });
        expect(query('tree-node-unit:11')).toBeTruthy();
    });

    it('should reveal a selection made from outside the tree by opening its ancestors', () => {
        // This is how a breadcrumb or a contextual jump arrives: the parent sets the selection and the tree has to open
        // the path to it. Nothing is expanded to begin with, so the node is not even rendered yet.
        expect(query('tree-node-coll:11:slides')).toBeFalsy();

        component.selection.set({ kind: 'collection', unitId: 11, key: 'slides' });
        fixture.detectChanges();

        expect(query('tree-node-lecture:20')).toBeTruthy();
        expect(query('tree-node-unit:11')).toBeTruthy();
        expect(query('tree-node-coll:11:slides')).toBeTruthy();
    });

    it('should reveal a unit selected from outside the tree', () => {
        component.selection.set({ kind: 'unit', unitId: 12 });
        fixture.detectChanges();

        // Unit 12 sits under the lecture that is not itself indexed; that branch must open too.
        expect(query('tree-node-unit:12')).toBeTruthy();
    });

    it('should toggle a node closed again', () => {
        click('tree-toggle-lecture:20');
        expect(query('tree-node-unit:11')).toBeTruthy();

        click('tree-toggle-lecture:20');
        expect(query('tree-node-unit:11')).toBeFalsy();
    });
});

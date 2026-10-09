import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { CourseIngestionBrowserTreeComponent } from './course-ingestion-browser-tree.component';

/** Tree-selection regressions, driven through the actual template and Angular effects. */
describe('course ingestion browser tree selection behaviour', () => {
    let fixture: ComponentFixture<CourseIngestionBrowserTreeComponent>;
    const query = (testId: string): HTMLElement | null => fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
    const click = (testId: string): void => {
        const button = query(testId);
        expect(button).toBeTruthy();
        button!.click();
        fixture.detectChanges();
    };

    beforeEach(() => {
        TestBed.configureTestingModule({ imports: [CourseIngestionBrowserTreeComponent], providers: [provideTranslateService()] });
        fixture = TestBed.createComponent(CourseIngestionBrowserTreeComponent);
        fixture.componentRef.setInput('entities', [
            { type: 'lecture', entityId: 20, title: 'Existing lecture', expected: true },
            { type: 'lecture_unit', entityId: 10, title: 'Deleted unit still indexed', lectureId: 20, expected: false },
        ]);
        fixture.componentRef.setInput('contentPresence', [{ key: 'slides', unitIds: [10], orphanedUnitIds: [10] }]);
        fixture.componentRef.setInput('typeCounts', [
            { type: 'lecture', expected: 1, indexed: 1, missing: 0, orphaned: 0 },
            { type: 'lecture_unit', expected: 0, indexed: 1, missing: 0, orphaned: 1 },
            { type: 'slides', expected: 0, indexed: 1, missing: 0, orphaned: 1 },
        ]);
        fixture.componentRef.setInput('contentGaps', []);
        fixture.componentRef.setInput('missingEntities', []);
        fixture.detectChanges();
    });

    it('does not mark an orphaned indexed unit and its branch complete', () => {
        click('tree-toggle-lecture:20');
        expect(query('tree-dot-unit:10')?.className).toContain('text-state-danger');
        expect(query('tree-dot-lecture:20')?.className).toContain('text-state-danger');
    });

    it('allows collapsing a selected lecture after opening its detail', () => {
        click('tree-node-lecture:20');
        expect(query('tree-node-unit:10')).toBeTruthy();
        click('tree-toggle-lecture:20');
        expect(query('tree-node-unit:10')).toBeFalsy();
    });

    it('reveals a selection after the tree data arrives and after the parent sends a new selection object', () => {
        fixture.componentRef.setInput('entities', []);
        fixture.componentRef.setInput('contentPresence', []);
        fixture.componentInstance.selection.set({ kind: 'collection', unitId: 10, key: 'slides' });
        fixture.detectChanges();
        expect(query('tree-node-unit:10')).toBeFalsy();

        fixture.componentRef.setInput('entities', [
            { type: 'lecture', entityId: 20, title: 'Existing lecture', expected: true },
            { type: 'lecture_unit', entityId: 10, title: 'Deleted unit still indexed', lectureId: 20, expected: false },
        ]);
        fixture.componentRef.setInput('contentPresence', [{ key: 'slides', unitIds: [10] }]);
        fixture.detectChanges();
        expect(query('tree-node-unit:10')).toBeTruthy();

        click('tree-toggle-lecture:20');
        expect(query('tree-node-unit:10')).toBeFalsy();
        fixture.componentInstance.selection.set({ kind: 'collection', unitId: 10, key: 'slides' });
        fixture.detectChanges();
        expect(query('tree-node-unit:10')).toBeTruthy();
    });
});

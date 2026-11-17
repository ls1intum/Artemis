import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityPolicyDocument } from 'app/programming/shared/entities/security-policy.model';
import { RowSource, SourcedRow, withRows } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';
import { SecurityResourceAccessComponent } from 'app/programming/manage/update/update-components/security/policies/security-resource-access.component';

const READ = { read: true, overwrite: false, create: false, execute: false, delete: false };

const BASE: SecurityPolicyDocument = {
    policyPath: 'SecurityPolicy.yaml',
    files: [
        { path: 'a.txt', ...READ },
        { path: 'b.txt', ...READ },
        { path: 'c.txt', ...READ },
    ],
};

type Handlers = {
    onTabChange(value: string): void;
    openAdd(): void;
    openEdit(row: SourcedRow): void;
    openDelete(row: SourcedRow): void;
    onSaved(rows: unknown[]): void;
    onDeleteConfirmed(): void;
    undoDelete(row: SourcedRow): void;
};

describe('SecurityResourceAccessComponent', () => {
    let fixture: ComponentFixture<SecurityResourceAccessComponent>;
    let comp: SecurityResourceAccessComponent;
    let handlers: Handlers;
    let emitted: SecurityPolicyDocument[];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityResourceAccessComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        emitted = [];
        comp.documentChange.subscribe((document) => {
            emitted.push(document);
            fixture.componentRef.setInput('document', document);
        });
        fixture.componentRef.setInput('base', BASE);
        fixture.componentRef.setInput('document', BASE);
        fixture.detectChanges();
    });

    function rows(): HTMLElement[] {
        return Array.from(fixture.nativeElement.querySelectorAll('[data-testid="security-resource-row"]'));
    }

    it('lists the rows of the active category with their source', () => {
        fixture.componentRef.setInput(
            'document',
            withRows(BASE, 'files', [
                { path: 'a.txt', ...READ, overwrite: true },
                { path: 'c.txt', ...READ },
            ]),
        );
        fixture.detectChanges();

        expect(comp.activeRows().map((row) => [row.key, row.source])).toEqual([
            ['a.txt', RowSource.CHANGED],
            ['c.txt', RowSource.COMMITTED],
            ['b.txt', RowSource.REMOVED],
        ]);
        expect(comp.tabCounts().files).toEqual({ rows: 2, changed: true });
        expect(rows()).toHaveLength(3);
        expect(fixture.nativeElement.querySelector('[data-testid="security-resource-undo"]')).not.toBeNull();
    });

    it('adds rows, replaces the edited row and keeps the order', () => {
        handlers.openAdd();
        handlers.onSaved([{ path: 'd.txt', ...READ }]);
        expect(emitted.at(-1)?.files?.map((file) => file.path)).toEqual(['a.txt', 'b.txt', 'c.txt', 'd.txt']);

        handlers.openEdit(comp.activeRows()[1]);
        handlers.onSaved([{ path: 'b2.txt', ...READ }]);
        expect(emitted.at(-1)?.files?.map((file) => file.path)).toEqual(['a.txt', 'b2.txt', 'c.txt', 'd.txt']);
    });

    it('deletes a row after confirming and restores it at its committed position', () => {
        handlers.openDelete(comp.activeRows()[1]);
        expect(comp.confirmOpen()).toBe(true);
        expect(comp.deleteDetails().map((detail) => detail.value)).toEqual(['b.txt', 'artemisApp.programmingExercise.security.advanced.resources.granted.read']);
        expect(comp.deleteWarning()).toEqual({ key: 'artemisApp.programmingExercise.security.advanced.resources.delete.warning.files', params: { target: 'b.txt' } });

        handlers.onDeleteConfirmed();
        expect(emitted.at(-1)?.files?.map((file) => file.path)).toEqual(['a.txt', 'c.txt']);

        const removed = comp.activeRows().find((row) => row.source === RowSource.REMOVED)!;
        handlers.undoDelete(removed);
        expect(emitted.at(-1)?.files?.map((file) => file.path)).toEqual(['a.txt', 'b.txt', 'c.txt']);
    });

    it('switches categories and shows the empty state of a category without rows', () => {
        handlers.onTabChange('packages');
        handlers.onTabChange('unknown');
        fixture.detectChanges();

        expect(comp.activeTab()).toBe('packages');
        expect(rows()).toHaveLength(0);
        expect(fixture.nativeElement.querySelector('[data-testid="security-resource-empty"]')).not.toBeNull();
    });

    it('renders the rows of every category and names their fields when deleting them', () => {
        let document = withRows(BASE, 'network', [{ host: 'localhost', port: 8080, open: true, send: true, receive: false }]);
        document = withRows(document, 'commands', [{ command: 'python3', arguments: [] }]);
        document = withRows(document, 'threads', [{ threadClass: 'java.lang.Thread', maxThreads: 2 }]);
        document = withRows(document, 'packages', [{ packageName: 'org.apache.commons' }]);
        fixture.componentRef.setInput('document', document);
        const details: Record<string, string[]> = {};

        for (const kind of ['network', 'commands', 'threads', 'packages']) {
            handlers.onTabChange(kind);
            fixture.detectChanges();
            expect(rows()).toHaveLength(1);
            handlers.openDelete(comp.activeRows()[0]);
            details[kind] = comp.deleteDetails().map((detail) => detail.value);
        }

        expect(details).toEqual({
            network: [
                'localhost',
                '8080',
                'artemisApp.programmingExercise.security.advanced.resources.granted.open, artemisApp.programmingExercise.security.advanced.resources.granted.send',
            ],
            commands: ['python3', 'artemisApp.programmingExercise.security.advanced.resources.noArguments'],
            threads: ['2', 'java.lang.Thread'],
            packages: ['org.apache.commons'],
        });
    });

    it('names the row in the deletion title and in the sentence of the question in the form of each language', () => {
        const row = 'artemisApp.programmingExercise.security.advanced.row.files';

        expect(comp.deleteHeader().params).toEqual({ noun: `${row}.noun` });
        expect(comp.deleteQuestion().params).toEqual({ noun: `${row}.nounInSentence`, tab: 'artemisApp.programmingExercise.security.advanced.resources.tab.files' });
    });

    it('tags the paths and scripts that already have a row for Browse…', () => {
        expect([...comp.browseBadges().keys()]).toEqual(['a.txt', 'b.txt', 'c.txt']);

        fixture.componentRef.setInput('document', withRows(BASE, 'commands', [{ command: 'bash', arguments: ['run.sh'] }]));
        handlers.onTabChange('commands');
        expect([...comp.browseBadges().keys()]).toEqual(['bash', 'run.sh']);
    });
});

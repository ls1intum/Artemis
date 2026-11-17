import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityFilePermission } from 'app/programming/shared/entities/security-policy.model';
import { ResourceKind, ResourceRow, joinArguments, splitArguments } from 'app/programming/manage/update/update-components/security/policies/security-policy-editor.util';
import { SecurityPermissionDialogComponent, joinList, splitList } from 'app/programming/manage/update/update-components/security/policies/security-permission-dialog.component';

const PROBLEM = 'artemisApp.programmingExercise.security.advanced.row.problem.';

/** Exposes the protected handlers the template calls. */
type Handlers = { setFileAction(action: string, granted: boolean): void; setNetworkAction(action: string, granted: boolean): void; save(): void; onBrowsed(paths: string[]): void };

describe('SecurityPermissionDialogComponent', () => {
    let fixture: ComponentFixture<SecurityPermissionDialogComponent>;
    let comp: SecurityPermissionDialogComponent;
    let handlers: Handlers;
    let saved: ResourceRow[][];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
    });

    function open(kind: ResourceKind, editing?: ResourceRow, existingKeys: string[] = []): void {
        fixture = TestBed.createComponent(SecurityPermissionDialogComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        saved = [];
        comp.saved.subscribe((rows) => saved.push(rows));
        fixture.componentRef.setInput('kind', kind);
        fixture.componentRef.setInput('editing', editing);
        fixture.componentRef.setInput('existingKeys', new Set(existingKeys));
        comp.visible.set(true);
        fixture.detectChanges();
    }

    it('adds one row per comma-separated path with the chosen actions', () => {
        open('files');
        comp.path.set('data/input.txt, logs/ ,');
        expect(comp.grantsNothing()).toBe(true);
        handlers.setFileAction('read', true);
        handlers.setFileAction('create', true);
        fixture.detectChanges();

        handlers.save();

        expect(saved).toEqual([
            [
                { path: 'data/input.txt', read: true, overwrite: false, create: true, execute: false, delete: false },
                { path: 'logs/', read: true, overwrite: false, create: true, execute: false, delete: false },
            ],
        ]);
        expect(comp.visible()).toBe(false);
        expect(comp.warningText()).toBe('artemisApp.programmingExercise.security.advanced.row.warning');
    });

    it('does not save a path that already has a row, but keeps the edited row itself', () => {
        open('files', undefined, ['data/input.txt']);
        comp.path.set('data/input.txt');
        handlers.setFileAction('read', true);
        handlers.save();
        fixture.detectChanges();

        expect(saved).toEqual([]);
        expect(comp.problem()).toBe(PROBLEM + 'duplicate');
        expect(fixture.nativeElement.ownerDocument.querySelector('[data-testid="security-permission-problem"]')).not.toBeNull();

        const edited: SecurityFilePermission = { path: 'data/input.txt', read: true, overwrite: false, create: false, execute: false, delete: false };
        open('files', edited, ['data/input.txt']);
        expect(comp.path()).toBe('data/input.txt');
        expect(comp.fileActions().read).toBe(true);
        handlers.setFileAction('overwrite', true);
        handlers.save();
        expect(saved[0]).toEqual([{ ...edited, overwrite: true }]);
    });

    it('refuses the same entry twice in one save', () => {
        open('files');
        comp.path.set('a.txt, a.txt');
        handlers.setFileAction('read', true);

        expect(comp.problem()).toBe(PROBLEM + 'duplicate');
    });

    it('checks the host and the port of an endpoint', () => {
        open('network');
        comp.host.set('local host');
        expect(comp.problem()).toBe(PROBLEM + 'hostRequired');
        comp.host.set('localhost');
        comp.portText.set('80.');
        expect(comp.portText()).toBe('80.');
        expect(comp.problem()).toBe(PROBLEM + 'portRange');
        comp.portText.set('70000');
        expect(comp.problem()).toBe(PROBLEM + 'portRange');
        comp.portText.set('8080');
        handlers.setNetworkAction('open', true);

        handlers.save();

        expect(saved).toEqual([[{ host: 'localhost', port: 8080, open: true, send: false, receive: false }]]);
    });

    it('adds commands with their exact arguments, thread classes and packages', () => {
        open('commands');
        comp.command.set('python3, bash');
        comp.argumentsText.set('  run.py   --fast ');
        handlers.save();
        expect(saved[0]).toEqual([
            { command: 'python3', arguments: ['run.py', '--fast'] },
            { command: 'bash', arguments: ['run.py', '--fast'] },
        ]);

        open('threads');
        comp.threadClass.set('java.lang.Thread');
        comp.maxThreadsText.set('0');
        expect(comp.problem()).toBe(PROBLEM + 'threadCount');
        comp.maxThreadsText.set('4');
        handlers.save();
        expect(saved[0]).toEqual([{ threadClass: 'java.lang.Thread', maxThreads: 4 }]);

        open('packages');
        comp.packageName.set('org apache');
        expect(comp.problem()).toBe(PROBLEM + 'packageName');
        comp.packageName.set('org.apache.commons.lang3');
        handlers.save();
        expect(saved[0]).toEqual([{ packageName: 'org.apache.commons.lang3' }]);
    });

    it('puts the browsed paths into the field and asks for the template files once', () => {
        open('files');
        const requested = vi.fn();
        comp.templateFilesRequested.subscribe(requested);

        (comp as unknown as { openBrowse(): void }).openBrowse();
        handlers.onBrowsed(['src/a.txt', 'src/b.txt']);

        expect(requested).toHaveBeenCalledOnce();
        expect(comp.browseOpen()).toBe(true);
        expect(comp.browsedEntries()).toEqual(['src/a.txt', 'src/b.txt']);
    });

    it('keeps a browsed file name with a comma as one path, so the row grants exactly the selected file', () => {
        open('files');
        handlers.onBrowsed(['data/a,b.csv', 'src/']);
        handlers.setFileAction('read', true);

        expect(comp.browsedEntries()).toEqual(['data/a,b.csv', 'src/']);
        handlers.save();
        expect(saved[0].map((row) => (row as SecurityFilePermission).path)).toEqual(['data/a,b.csv', 'src/']);
    });

    it('names an unclosed double quote of a list instead of guessing its entries', () => {
        open('commands');
        comp.command.set('"run,me.sh');

        expect(comp.problem()).toBe(PROBLEM + 'listQuote');
        expect(comp.browsedEntries()).toEqual([]);
    });

    it('splits lists and arguments', () => {
        expect(splitList(' a , ,b ')).toEqual(['a', 'b']);
        expect(splitList(' "a,b.csv" , " c " ,d e, "x""y"')).toEqual(['a,b.csv', ' c ', 'd e', 'x"y']);
        expect(splitList('"open')).toBeUndefined();
        for (const entries of [['data/a,b.csv', 'logs/'], [' spaced ', 'say "hi"', 'plain/path.txt'], ['scripts/run.sh']]) {
            expect(splitList(joinList(entries))).toEqual(entries);
        }
        expect(joinList(['a.txt', 'b,c.txt'])).toBe('a.txt, "b,c.txt"');
        expect(splitArguments('')).toEqual([]);
        expect(splitArguments(' -c  "x y" "" a\\ b ')).toEqual(['-c', 'x y', '', 'a b']);
        expect(splitArguments('"open')).toBeUndefined();
        for (const argumentList of [['data/input file.txt'], ['', 'a"b', 'c\\d'], ['--fast']]) {
            expect(splitArguments(joinArguments(argumentList))).toEqual(argumentList);
        }
    });
});

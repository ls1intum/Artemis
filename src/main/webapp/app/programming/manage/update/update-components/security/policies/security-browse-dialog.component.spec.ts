import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityBrowseDialogComponent, isScript } from 'app/programming/manage/update/update-components/security/policies/security-browse-dialog.component';

const FILES = ['src/Main.java', 'scripts/run.sh', 'scripts/tools/setup.py', 'data/input.txt', 'gradlew'];

type Node = { path: string; folder: boolean; children: Node[] };
type Handlers = {
    toggleExpanded(node: Node): void;
    toggleSelected(node: Node, checked: boolean): void;
    isSelected(node: Node): boolean;
    isPartlySelected(node: Node): boolean;
    confirm(): void;
};

describe('SecurityBrowseDialogComponent', () => {
    let fixture: ComponentFixture<SecurityBrowseDialogComponent>;
    let comp: SecurityBrowseDialogComponent;
    let handlers: Handlers;
    let selected: string[][];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityBrowseDialogComponent);
        comp = fixture.componentInstance;
        handlers = comp as unknown as Handlers;
        selected = [];
        comp.selected.subscribe((paths) => selected.push(paths));
        fixture.componentRef.setInput('files', FILES);
        fixture.componentRef.setInput('initialSelection', ['data/input.txt']);
        comp.visible.set(true);
        fixture.detectChanges();
    });

    function node(path: string): Node {
        const find = (nodes: Node[]): Node | undefined => nodes.map((candidate) => (candidate.path === path ? candidate : find(candidate.children))).find(Boolean);
        return find(comp.tree() as Node[])!;
    }

    it('builds a tree with folders first and expands folders on demand', () => {
        expect(comp.visibleNodes().map((row) => row.node.path)).toEqual(['data/', 'scripts/', 'src/', 'gradlew']);

        handlers.toggleExpanded(node('scripts/'));

        expect(comp.visibleNodes().map((row) => row.node.path)).toEqual(['data/', 'scripts/', 'scripts/tools/', 'scripts/run.sh', 'src/', 'gradlew']);
        expect(comp.selectedPaths()).toEqual(['data/input.txt']);
    });

    it('selects files and folders as paths of their own', () => {
        handlers.toggleSelected(node('src/'), true);
        handlers.toggleSelected(node('data/input.txt'), false);
        handlers.confirm();

        expect(selected).toEqual([['src/']]);
        expect(comp.visible()).toBe(false);
    });

    it('lists only scripts and selects every script of a folder', () => {
        fixture.componentRef.setInput('mode', 'scripts');
        fixture.detectChanges();

        expect(comp.tree().map((entry) => entry.path)).toEqual(['scripts/', 'gradlew']);
        handlers.toggleSelected(node('scripts/tools/'), true);
        expect(handlers.isPartlySelected(node('scripts/'))).toBe(true);
        handlers.toggleSelected(node('scripts/'), true);
        expect(handlers.isSelected(node('scripts/'))).toBe(true);
        expect(comp.selectedPaths()).toEqual(['data/input.txt', 'scripts/run.sh', 'scripts/tools/setup.py']);
    });

    it('clears the field when everything it named is deselected', () => {
        handlers.toggleSelected(node('data/input.txt'), false);

        expect(comp.canConfirm()).toBe(true);
        handlers.confirm();
        expect(selected).toEqual([[]]);
    });

    it('lets the summary remove a selected entry that the tree has no row for', () => {
        fixture.componentRef.setInput('mode', 'scripts');
        fixture.componentRef.setInput('initialSelection', ['python3']);
        comp.visible.set(false);
        comp.visible.set(true);
        fixture.detectChanges();
        handlers.toggleSelected(node('scripts/run.sh'), true);
        fixture.detectChanges();
        const root = (fixture.nativeElement as HTMLElement).ownerDocument;

        // A command typed into the field has no checkbox, so only the summary can drop it.
        expect(comp.unlistedSelection()).toEqual(['python3']);
        root.querySelector<HTMLButtonElement>('[data-testid="security-browse-remove"]')!.click();
        handlers.confirm();
        expect(selected).toEqual([['scripts/run.sh']]);
    });

    it('reports a failed listing with Retry instead of showing it as empty', () => {
        let retried = 0;
        comp.retryRequested.subscribe(() => retried++);
        fixture.componentRef.setInput('files', undefined);
        fixture.componentRef.setInput('failed', true);
        fixture.detectChanges();
        const root = (fixture.nativeElement as HTMLElement).ownerDocument;

        expect(root.querySelector('[data-testid="security-browse-loading"]')).toBeNull();
        root.querySelector<HTMLButtonElement>('[data-testid="security-browse-retry"]')!.click();
        expect(retried).toBe(1);

        fixture.componentRef.setInput('failed', false);
        fixture.detectChanges();
        expect(root.querySelector('[data-testid="security-browse-retry"]')).toBeNull();
        expect(root.querySelector('[data-testid="security-browse-loading"]')).not.toBeNull();
    });

    it('shows every match of the search with its folders', () => {
        comp.search.set('setup');
        fixture.detectChanges();

        expect(comp.visibleNodes().map((row) => row.node.path)).toEqual(['scripts/', 'scripts/tools/', 'scripts/tools/setup.py']);
        // The search opens every folder of a match, so the folders offer no control that could not close them, and the tree after the search stays as before.
        expect((fixture.nativeElement as HTMLElement).ownerDocument.querySelector('.sp-twisty')).toBeNull();
        handlers.toggleExpanded(node('scripts/'));
        comp.search.set('');
        expect(comp.visibleNodes().map((row) => row.node.path)).toEqual(['data/', 'scripts/', 'src/', 'gradlew']);
        expect(isScript('a/run.sh')).toBe(true);
        expect(isScript('a/Main.java')).toBe(false);
    });
});

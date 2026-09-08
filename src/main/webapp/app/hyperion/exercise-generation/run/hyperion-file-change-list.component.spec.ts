import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HyperionFileChangeListComponent } from 'app/hyperion/exercise-generation/run/hyperion-file-change-list.component';
import { HyperionArtifactFile, artifactFiles } from 'app/hyperion/exercise-generation/artifacts/hyperion-artifact-file';
import { ExerciseGenerationFileChange, HyperionFileChangeAction, HyperionFileChangeRepo } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

function change(
    repo: HyperionFileChangeRepo,
    path: string,
    { action = 'write' as HyperionFileChangeAction, turn = 1, timestamp = '2026-07-13T09:00:00Z' } = {},
): ExerciseGenerationFileChange {
    return { type: 'FILE_CHANGE', repo, path, action, turn, timestamp };
}

const THREE_REPOS = artifactFiles([
    change('solution', 'solution/src/de/tum/Loan.java'),
    change('template', 'template/src/de/tum/Loan.java', { action: 'edit' }),
    change('tests', 'tests/src/de/tum/LoanTest.java', { turn: 9, timestamp: '2026-07-13T09:09:00Z' }),
]);

describe('HyperionFileChangeListComponent', () => {
    let fixture: ComponentFixture<HyperionFileChangeListComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [HyperionFileChangeListComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        fixture = TestBed.createComponent(HyperionFileChangeListComponent);
    });

    function render(files: readonly HyperionArtifactFile[], inputs: Record<string, unknown> = {}): HTMLElement {
        fixture.componentRef.setInput('files', files);
        for (const [name, value] of Object.entries(inputs)) {
            fixture.componentRef.setInput(name, value);
        }
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    function rows(host: HTMLElement): HTMLElement[] {
        return [...host.querySelectorAll<HTMLElement>('[data-testid="hyperion-file-row"]')];
    }

    describe('empty', () => {
        it('lets the host say why the list is empty, because only the host knows', () => {
            const host = render([], {
                emptyTitleKey: 'artemisApp.hyperion.generation.artifacts.filesNone',
                emptyDescriptionKey: 'artemisApp.hyperion.generation.artifacts.filesNoneHint',
            });

            expect(host.querySelector('[data-testid="hyperion-files-empty"]')).not.toBeNull();
            expect(host.textContent).toContain('artifacts.filesNone');
            expect(host.textContent).toContain('artifacts.filesNoneHint');
            expect(host.querySelector('[data-testid="hyperion-file-change-list"]')).toBeNull();
        });

        it('defaults to the "not written yet" sentence rather than to a bare apology', () => {
            expect(render([]).textContent).toContain('artifacts.filesPendingHint');
        });
    });

    describe('grouping', () => {
        it('groups by repository in the one order every Hyperion surface uses', () => {
            const host = render(THREE_REPOS);

            expect([...host.querySelectorAll('[data-repo]')].map((group) => group.getAttribute('data-repo'))).toEqual(['solution', 'template', 'tests']);
        });

        it('gives each group its count and names its list for a screen reader', () => {
            const host = render(THREE_REPOS);

            const group = host.querySelector('[data-repo="solution"]')!;
            const label = group.querySelector('p')!;
            expect(label.textContent).toContain('repo.solution');
            expect(label.textContent).toContain('1');
            expect(group.querySelector('ul')!.getAttribute('aria-labelledby')).toBe(label.id);
            expect(label.id).not.toBe('');
        });

        it('gives two instances of the list distinct label ids, so one cannot name the other’s group', async () => {
            render(THREE_REPOS);
            const second = TestBed.createComponent(HyperionFileChangeListComponent);
            second.componentRef.setInput('files', THREE_REPOS);
            second.detectChanges();

            const firstId = (fixture.nativeElement as HTMLElement).querySelector('[data-repo="solution"] p')!.id;
            const secondId = (second.nativeElement as HTMLElement).querySelector('[data-repo="solution"] p')!.id;
            expect(firstId).not.toBe(secondId);
        });

        it('keeps the list semantics explicit, because a flex list without markers loses them', () => {
            expect(render(THREE_REPOS).querySelector('ul')!.getAttribute('role')).toBe('list');
        });
    });

    describe('rows', () => {
        it('clips the directory but never the file name', () => {
            const row = rows(render(THREE_REPOS))[0];

            const parts = row.querySelectorAll('span span');
            expect(parts[0].className).toContain('truncate');
            expect(parts[0].textContent).toBe('src/de/tum/');
            expect(parts[1].className).not.toContain('truncate');
            expect(parts[1].textContent).toBe('Loan.java');
        });

        it('labels each row with what happened to the file', () => {
            const host = render(THREE_REPOS);

            expect(host.querySelector('[data-repo="solution"]')!.textContent).toContain('action.write');
            expect(host.querySelector('[data-repo="template"]')!.textContent).toContain('action.edit');
        });

        it('calls the newest write "writing now" while the run is going', () => {
            const host = render(THREE_REPOS, { running: true });

            const recent = host.querySelectorAll('[data-recent]');
            expect(recent).toHaveLength(1);
            expect(recent[0].textContent).toContain('LoanTest.java');
            expect(recent[0].textContent).toContain('artifacts.writingNow');
        });

        it('calls the same row "written last" once the run has stopped, rather than claiming live work', () => {
            const host = render(THREE_REPOS, { running: false });

            expect(host.querySelector('[data-recent]')!.textContent).toContain('artifacts.writtenLast');
            expect(host.textContent).not.toContain('artifacts.writingNow');
        });

        it('marks the selected row as current for assistive technology', () => {
            const host = render(THREE_REPOS, { selectedKey: THREE_REPOS[0].key });

            const current = host.querySelectorAll('[aria-current="true"]');
            expect(current).toHaveLength(1);
            expect(current[0].textContent).toContain('Loan.java');
        });
    });

    describe('what a row can do', () => {
        it('makes the whole row the control, and emits the file it stands for', () => {
            const selected = vi.fn();
            fixture.componentInstance.fileSelected.subscribe(selected);
            const host = render(THREE_REPOS);

            const row = rows(host)[0];
            expect(row.tagName).toBe('BUTTON');
            row.click();

            expect(selected).toHaveBeenCalledTimes(1);
            expect(selected.mock.calls[0][0]).toMatchObject({ repo: 'solution', name: 'Loan.java' });
        });

        it('renders rows the host cannot act on as plain elements rather than as dead controls', () => {
            const selected = vi.fn();
            fixture.componentInstance.fileSelected.subscribe(selected);
            const host = render(THREE_REPOS, { actionableKeys: [] });

            expect(rows(host).map((row) => row.tagName)).toEqual(['DIV', 'DIV', 'DIV']);
            rows(host).forEach((row) => row.click());
            expect(selected).not.toHaveBeenCalled();
        });

        it('acts on exactly the rows the host named', () => {
            const selected = vi.fn();
            fixture.componentInstance.fileSelected.subscribe(selected);
            const host = render(THREE_REPOS, { actionableKeys: [THREE_REPOS[2].key] });

            expect(rows(host).map((row) => row.tagName)).toEqual(['DIV', 'DIV', 'BUTTON']);
            rows(host).forEach((row) => row.click());

            expect(selected).toHaveBeenCalledTimes(1);
            expect(selected.mock.calls[0][0]).toMatchObject({ name: 'LoanTest.java' });
        });
    });

    it('takes the docked-panel density on the same rows rather than through a second component', () => {
        expect(rows(render(THREE_REPOS, { density: 'compact' }))[0].className).toContain('hyperion-artifact-row-compact');
        expect(rows(render(THREE_REPOS, { density: 'comfortable' }))[0].className).not.toContain('hyperion-artifact-row-compact');
    });
});

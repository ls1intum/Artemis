import { beforeEach, describe, expect, it } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

import { SecurityVersionCompatibilityVerdict } from 'app/programming/shared/entities/security-framework-config.model';
import { SecurityPolicyPreview } from 'app/programming/shared/entities/security-policy.model';
import { SecurityReviewDialogComponent } from 'app/programming/manage/update/update-components/security/policies/security-review-dialog.component';

const PREVIEW: SecurityPolicyPreview = {
    verdict: SecurityVersionCompatibilityVerdict.COMPATIBLE,
    frameworkVersion: '2.2.1',
    files: [
        { path: 'SecurityPolicy.yaml', before: 'a: 1\nb: 2\n', after: 'a: 1\nb: 3\n' },
        { path: 'Unchanged.yaml', before: 'x\n', after: 'x\n' },
        { path: 'Stress.yaml', after: 'c: 1\n' },
    ],
};

describe('SecurityReviewDialogComponent', () => {
    let fixture: ComponentFixture<SecurityReviewDialogComponent>;
    let comp: SecurityReviewDialogComponent;
    let commits: string[];

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [{ provide: TranslateService, useClass: MockTranslateService }] });
        fixture = TestBed.createComponent(SecurityReviewDialogComponent);
        comp = fixture.componentInstance;
        commits = [];
        comp.commitRequested.subscribe((message) => commits.push(message));
        fixture.componentRef.setInput('preview', PREVIEW);
        fixture.componentRef.setInput('defaultMessage', 'Update the security policies');
        fixture.componentRef.setInput('assignments', [{ sign: '+', entry: 'de.a.StressTest', file: 'Stress.yaml' }]);
        comp.visible.set(true);
        fixture.detectChanges();
    });

    function commit(): void {
        (comp as unknown as { commit(): void }).commit();
    }

    it('diffs the changed files only and commits with the edited message', () => {
        expect(comp.diffs().map((diff) => [diff.path, diff.added, diff.removed])).toEqual([
            ['SecurityPolicy.yaml', 1, 1],
            ['Stress.yaml', 1, 0],
        ]);
        expect(comp.assignmentsAdded()).toBe(1);
        expect(comp.message()).toBe('Update the security policies');

        comp.message.set('  Allow reading the input  ');
        commit();

        expect(commits).toEqual(['Allow reading the input']);
    });

    it('does not commit while a problem or an incompatible version blocks it or without a message', () => {
        comp.message.set(' ');
        commit();
        fixture.componentRef.setInput('problems', ['Stress has no test case.']);
        comp.message.set('Update');
        commit();
        fixture.componentRef.setInput('problems', []);
        fixture.componentRef.setInput('preview', { ...PREVIEW, verdict: SecurityVersionCompatibilityVerdict.INCOMPATIBLE, problems: ['outputMirroring'] });
        commit();

        expect(commits).toEqual([]);
        expect(comp.blocked()).toBe(true);
    });

    it('commits a split only once its released test cases are listed, and asks for them again on Retry', () => {
        let retries = 0;
        comp.assignmentsRetry.subscribe(() => retries++);
        fixture.componentRef.setInput('assignmentsState', 'loading');
        fixture.detectChanges();
        const root = (fixture.nativeElement as HTMLElement).ownerDocument;
        expect(root.querySelector('[data-testid="security-review-assignments-loading"]')).not.toBeNull();
        commit();

        fixture.componentRef.setInput('assignmentsState', 'failed');
        fixture.detectChanges();
        root.querySelector<HTMLButtonElement>('[data-testid="security-review-assignments-retry"]')!.click();
        commit();
        expect(retries).toBe(1);
        expect(commits).toEqual([]);

        fixture.componentRef.setInput('assignmentsState', 'ready');
        commit();
        expect(commits).toEqual(['Update the security policies']);
    });

    it('waits for the preview', () => {
        fixture.componentRef.setInput('preview', undefined);

        expect(comp.canCommit()).toBe(false);
        expect(comp.diffs()).toEqual([]);
    });
});

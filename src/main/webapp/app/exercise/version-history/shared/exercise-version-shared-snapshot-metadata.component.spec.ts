import { Component, input } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { expect, vi } from 'vitest';
import { ExerciseVersionSharedSnapshotMetadataComponent } from 'app/exercise/version-history/shared/exercise-version-shared-snapshot-metadata.component';
import { ExerciseVersionMarkdownDiffComponent } from 'app/exercise/version-history/shared/exercise-version-markdown-diff.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

@Component({
    selector: 'jhi-exercise-version-markdown-diff',
    template: '',
    standalone: true,
})
class MockExerciseVersionMarkdownDiffComponent {
    readonly original = input<string | undefined>();
    readonly modified = input<string | undefined>();
    readonly domainActions = input<any[]>([]);
}

describe('ExerciseVersionSharedSnapshotMetadataComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVersionSharedSnapshotMetadataComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVersionSharedSnapshotMetadataComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .overrideComponent(ExerciseVersionSharedSnapshotMetadataComponent, {
                remove: { imports: [ExerciseVersionMarkdownDiffComponent] },
                add: { imports: [MockExerciseVersionMarkdownDiffComponent] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ExerciseVersionSharedSnapshotMetadataComponent);
        fixture.componentRef.setInput('snapshot', {
            id: 23,
            title: 'Version title',
            shortName: 'V23',
            channelName: 'chan-1',
            maxPoints: 10,
            difficulty: 'EASY' as any,
            mode: 'INDIVIDUAL' as any,
            categories: ['algorithms'],
            problemStatement: 'Hello **world**',
            gradingInstructions: 'Use tests',
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render shared snapshot values', () => {
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('Version title');
        expect(text).toContain('V23');
        expect(text).toContain('chan-1');
        expect(text).toContain('algorithms');
    });

    it('should only render changed simple fields in diff mode', () => {
        const component = fixture.componentInstance;
        fixture.componentRef.setInput('previousSnapshot', {
            id: 22,
            title: 'Version title',
            shortName: 'V23',
            maxPoints: 10,
            difficulty: 'EASY' as any,
            mode: 'INDIVIDUAL' as any,
            categories: ['algorithms'],
            channelName: 'chan-0',
            problemStatement: 'Hello **world**',
            gradingInstructions: 'Use tests',
        });
        fixture.componentRef.setInput('viewMode', 'changes');
        fixture.detectChanges();

        expect(component.generalFields()).toHaveLength(1);
        expect(component.generalFields()[0].currentDisplay).toBe('chan-1');
        expect(component.generalFields()[0].previousDisplay).toBe('chan-0');
    });

    it('should hide the entire host when diff mode has no visible shared changes', () => {
        const component = fixture.componentInstance;
        fixture.componentRef.setInput('previousSnapshot', {
            id: 22,
            title: 'Version title',
            shortName: 'V23',
            channelName: 'chan-1',
            maxPoints: 10,
            difficulty: 'EASY' as any,
            mode: 'INDIVIDUAL' as any,
            categories: ['algorithms'],
            problemStatement: 'Hello **world**',
            gradingInstructions: 'Use tests',
        });
        fixture.componentRef.setInput('viewMode', 'changes');
        fixture.detectChanges();

        expect(component.hasVisibleContent()).toBe(false);
        expect(fixture.nativeElement.textContent.trim()).toBe('');
    });
});

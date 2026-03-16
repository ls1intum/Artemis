import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { vi } from 'vitest';
import { ExerciseVersionSharedSnapshotMetadataComponent } from 'app/exercise/version-history/shared/exercise-version-shared-snapshot-metadata.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseVersionSharedSnapshotMetadataComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVersionSharedSnapshotMetadataComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVersionSharedSnapshotMetadataComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

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
});

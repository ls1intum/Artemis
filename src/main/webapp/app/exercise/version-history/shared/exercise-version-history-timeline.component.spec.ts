import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ExerciseVersionHistoryTimelineComponent } from 'app/exercise/version-history/shared/exercise-version-history-timeline.component';
import dayjs from 'dayjs/esm';
import { vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExerciseVersionHistoryTimelineComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ExerciseVersionHistoryTimelineComponent>;
    let component: ExerciseVersionHistoryTimelineComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExerciseVersionHistoryTimelineComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseVersionHistoryTimelineComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('versions', [
            {
                id: 5,
                author: { login: 'ed1', name: 'Editor One' },
                createdDate: dayjs('2026-03-04T11:00:00Z'),
            },
        ]);
        fixture.componentRef.setInput('selectedVersionId', 5);
        fixture.componentRef.setInput('hasMore', true);
        fixture.componentRef.setInput('loading', false);
        fixture.componentRef.setInput('loadingMore', false);
        fixture.componentRef.setInput('totalItems', 1);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should render version id and author', () => {
        const text = fixture.nativeElement.textContent;
        expect(text).toContain('#5');
        expect(text).toContain('Editor One');
    });

    it('should emit selected version id', () => {
        const emitSpy = vi.spyOn(component.selectVersion, 'emit');
        const versionButton: HTMLButtonElement = fixture.nativeElement.querySelector('.timeline-card');
        versionButton.click();
        expect(emitSpy).toHaveBeenCalledWith(5);
    });

    it('should emit loadMore on click', () => {
        const emitSpy = vi.spyOn(component.loadMore, 'emit');
        const loadMoreButton = fixture.nativeElement.querySelector('.timeline-load-more');
        loadMoreButton.click();
        expect(emitSpy).toHaveBeenCalled();
    });
});

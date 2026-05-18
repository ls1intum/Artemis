import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchResultItemComponent } from './search-result-item.component';
import { GlobalSearchResult } from 'app/openapi/model/globalSearchResult';
import { faCube } from '@fortawesome/free-solid-svg-icons';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SearchResultItemComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SearchResultItemComponent;
    let fixture: ComponentFixture<SearchResultItemComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SearchResultItemComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SearchResultItemComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('result', { id: '1', title: 'Test Result', type: 'exercise', metadata: {} } as GlobalSearchResult);
        fixture.componentRef.setInput('icon', faCube);
        fixture.componentRef.setInput('isSelected', false);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should compute metadata correctly', () => {
        fixture.componentRef.setInput('result', {
            id: '1',
            title: 'Test Result',
            type: 'exercise',
            metadata: {
                courseName: 'Test Course',
                dueDate: '2026-05-01T10:00:00Z',
                points: 10,
                difficulty: 'EASY',
            },
        } as GlobalSearchResult);
        fixture.detectChanges();

        expect(component['courseName']()).toBe('Test Course');
        expect(component['dueDate']()).toBe('2026-05-01T10:00:00Z');
        expect(component['points']()).toBe(10);
        expect(component['difficulty']()).toBe('EASY');
        expect(component['hasAnyMetadata']()).toBe(true);
        expect(component['showCourseSeparator']()).toBe(true);
        expect(component['showDatePointsSeparator']()).toBe(true);
        expect(component['showDifficultySeparator']()).toBe(true);
        expect(component['formattedDueDate']()).not.toBe('');
    });

    it('should handle missing metadata', () => {
        fixture.componentRef.setInput('result', { id: '1', title: 'Test Result', type: 'exercise' } as GlobalSearchResult);
        fixture.detectChanges();

        expect(component['courseName']()).toBeUndefined();
        expect(component['hasAnyMetadata']()).toBe(false);
        expect(component['showCourseSeparator']()).toBe(false);
    });

    it('should show start date only if due date is missing', () => {
        fixture.componentRef.setInput('result', {
            id: '1',
            title: 'Test Result',
            type: 'exercise',
            metadata: {
                startDate: '2026-04-01T10:00:00Z',
            },
        } as GlobalSearchResult);
        fixture.detectChanges();

        expect(component['showStartDateOnly']()).toBe(true);
        expect(component['formattedStartDate']()).not.toBe('');
    });

    it('should emit resultClick when clicked', () => {
        const spy = vi.spyOn(component.resultClick, 'emit');
        component['onClick']();
        expect(spy).toHaveBeenCalledWith(component.result());
    });
});

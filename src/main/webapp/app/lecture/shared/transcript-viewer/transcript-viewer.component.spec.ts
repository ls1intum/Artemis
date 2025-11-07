import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranscriptSegment, TranscriptViewerComponent } from './transcript-viewer.component';
import { TranslateModule } from '@ngx-translate/core';

describe('TranscriptViewerComponent', () => {
    let component: TranscriptViewerComponent;
    let fixture: ComponentFixture<TranscriptViewerComponent>;

    const mockSegments: TranscriptSegment[] = [
        { startTime: 0, endTime: 5, text: 'Hello world' },
        { startTime: 5, endTime: 10, text: 'This is a test' },
        { startTime: 10, endTime: 15, text: 'Angular component' },
    ];

    beforeEach(async () => {
        // Mock scrollIntoView for tests
        Element.prototype.scrollIntoView = jest.fn();

        await TestBed.configureTestingModule({
            imports: [TranscriptViewerComponent, TranslateModule.forRoot()],
        }).compileComponents();

        fixture = TestBed.createComponent(TranscriptViewerComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('transcriptSegments', mockSegments);
        fixture.componentRef.setInput('currentSegmentIndex', -1);
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should display all segments when no search query', () => {
        expect(component.filteredSegments()).toEqual(mockSegments);
    });

    it('should filter segments based on search query', () => {
        component.onSearchQueryChange('test');
        fixture.detectChanges();

        const filtered = component.filteredSegments();
        expect(filtered).toHaveLength(1);
        expect(filtered[0].text).toBe('This is a test');
    });

    it('should emit segment click event', () => {
        const segmentClickedSpy = jest.fn();
        component.segmentClicked.subscribe(segmentClickedSpy);

        component.onSegmentClick(5);

        expect(segmentClickedSpy).toHaveBeenCalledWith(5);
    });

    it('should clear search query', () => {
        component.onSearchQueryChange('test');
        expect(component.searchQuery()).toBe('test');

        component.clearSearch();
        expect(component.searchQuery()).toBe('');
        expect(component.currentSearchIndex()).toBe(0);
    });

    it('should navigate to next search result', () => {
        component.onSearchQueryChange('a'); // Matches "Angular component"
        fixture.detectChanges();

        expect(component.currentSearchIndex()).toBe(0);

        component.nextSearchResult();
        expect(component.currentSearchIndex()).toBe(0); // Wraps around with 1 result
    });

    it('should highlight search text', () => {
        component.onSearchQueryChange('test');
        const highlighted = component.highlightText('This is a test');

        expect(highlighted).toContain('<mark>');
        expect(highlighted).toContain('test');
    });

    it('should escape HTML in text', () => {
        const maliciousText = '<script>alert("xss")</script>';
        const highlighted = component.highlightText(maliciousText);

        expect(highlighted).not.toContain('<script>');
        expect(highlighted).toContain('&lt;');
        expect(highlighted).toContain('&gt;');
    });

    it('should identify current search result', () => {
        component.onSearchQueryChange('test');
        const filtered = component.filteredSegments();

        expect(component.isCurrentSearchResult(filtered[0])).toBeTrue();
        expect(component.isCurrentSearchResult(mockSegments[0])).toBeFalse();
    });
});

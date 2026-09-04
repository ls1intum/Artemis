import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SearchResultItemComponent } from './search-result-item.component';
import { GlobalSearchResult } from 'app/openapi/model/global-search-result';
import { faCube } from '@fortawesome/free-solid-svg-icons';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SearchResultItemComponent', () => {
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

    it('should not render an active anchor for markdown links in description', async () => {
        fixture.componentRef.setInput('result', {
            id: '1',
            title: 'Graph BFS Shortest Path',
            type: 'exercise',
            description:
                '# Graph BFS Shortest Path\n\n' +
                '*Implement* **BFS** ***to*** `find`\n' +
                '```\nfunction bfs(graph, start) {\n  const visited = new Set();\n}\n```\n' +
                '_shortest_ path in an unweighted graph. See [docs](https://example.com) for details.',
            metadata: {},
        } as GlobalSearchResult);
        fixture.detectChanges();

        const descriptionEl: HTMLElement = fixture.nativeElement.querySelector('.result-description');
        expect(descriptionEl).toBeTruthy();

        // The link text should be visible (markdown is rendered asynchronously via the lazy [jhiMarkdown] directive).
        await vi.waitFor(() => {
            fixture.detectChanges();
            expect(descriptionEl.textContent).toContain('docs');
        });

        // But no <a> element should be present
        const anchor = descriptionEl.querySelector('a');
        expect(anchor).toBeNull();
    });

    describe('cleanedDescription', () => {
        it.each([
            { case: 'heading', description: '# My Exercise\nThis is the body.' },
            { case: 'bold', description: '**My Exercise**\nThis is the body.' },
            { case: 'italic', description: '*My Exercise*\nThis is the body.' },
            { case: 'plain', description: 'My Exercise\nThis is the body.' },
        ])('should strip a $case first line matching the title', ({ description }) => {
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description,
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['cleanedDescription']()).toBe('This is the body.');
        });

        it.each([
            { case: 'description equals only the title heading', description: '# My Exercise' },
            { case: 'empty description', description: '' },
            { case: 'undefined description', description: undefined },
        ])('should return undefined when $case', ({ description }) => {
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description,
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['cleanedDescription']()).toBeUndefined();
        });

        it('should return the full description when the first line does not match the title', () => {
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description: '# Different Heading\nThis is the body.',
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['cleanedDescription']()).toBe('# Different Heading\nThis is the body.');
        });

        it('should truncate a long description and append ellipsis', () => {
            const longText = 'A'.repeat(400);
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description: longText,
            } as GlobalSearchResult);
            fixture.detectChanges();

            const result = component['cleanedDescription']()!;
            expect(result).toHaveLength(301); // 300 chars + ellipsis
            expect(result.endsWith('…')).toBe(true);
            expect(result).not.toContain('```');
        });

        it('should close an open fenced code block when truncating', () => {
            const description = 'Some intro\n```\n' + 'X'.repeat(400);
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description,
            } as GlobalSearchResult);
            fixture.detectChanges();

            const result = component['cleanedDescription']()!;
            expect(result.endsWith('…\n```')).toBe(true);
        });

        it('should not close code block when truncation lands after an even number of fences', () => {
            const description = '```\ncode\n```\n' + 'Y'.repeat(400);
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description,
            } as GlobalSearchResult);
            fixture.detectChanges();

            const result = component['cleanedDescription']()!;
            expect(result.endsWith('…')).toBe(true);
            expect(result.endsWith('…\n```')).toBe(false);
        });

        it('should close an open four-backtick fenced code block with four backticks when truncating', () => {
            const description = 'Some intro\n````\n' + 'X'.repeat(400);
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description,
            } as GlobalSearchResult);
            fixture.detectChanges();

            const result = component['cleanedDescription']()!;
            expect(result.endsWith('…\n````')).toBe(true);
            expect(result).not.toMatch(/…\n`{3}$/);
        });

        it('should not truncate a short description', () => {
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description: 'Short description.',
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['cleanedDescription']()).toBe('Short description.');
        });

        it('should truncate description after stripping title line', () => {
            const longBody = 'B'.repeat(400);
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'My Exercise',
                type: 'exercise',
                description: '# My Exercise\n' + longBody,
            } as GlobalSearchResult);
            fixture.detectChanges();

            const result = component['cleanedDescription']()!;
            expect(result).toHaveLength(301);
            expect(result.endsWith('…')).toBe(true);
        });
    });

    describe('lecture_content location line', () => {
        it('should render course, lecture name and the page location for a slide hit', () => {
            fixture.componentRef.setInput('result', {
                id: 'lecture-content-30-4',
                title: 'Introduction to Signals',
                type: 'lecture_content',
                metadata: { courseName: 'Advanced Web Development', lectureName: 'Angular Basics', pageNumber: 4 },
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['isLectureContent']()).toBe(true);
            const metadata: HTMLElement = fixture.nativeElement.querySelector('.result-metadata');
            expect(metadata.textContent).toContain('Advanced Web Development');
            expect(metadata.textContent).toContain('Angular Basics');
            expect(metadata.textContent).toContain('global.search.pageAbbreviation');
            expect(metadata.textContent).toContain('4');
            expect(metadata.textContent).not.toContain('global.search.results.video');
        });

        it('should render the displayMeta timestamp for a video hit', () => {
            fixture.componentRef.setInput('result', {
                id: 'lecture-content-31--1',
                title: 'B-Trees Explained',
                type: 'lecture_content',
                metadata: { courseName: 'Databases', lectureName: 'Indexing', pageNumber: -1, displayMeta: '3:41' },
            } as GlobalSearchResult);
            fixture.detectChanges();

            const metadata: HTMLElement = fixture.nativeElement.querySelector('.result-metadata');
            expect(metadata.textContent).toContain('Indexing');
            expect(metadata.textContent).toContain('3:41');
            expect(metadata.textContent).not.toContain('global.search.pageAbbreviation');
        });

        it('should fall back to the localized "Video" label when a video hit has no displayMeta', () => {
            fixture.componentRef.setInput('result', {
                id: 'lecture-content-31--1',
                title: 'B-Trees Explained',
                type: 'lecture_content',
                metadata: { courseName: 'Databases', lectureName: 'Indexing', pageNumber: -1 },
            } as GlobalSearchResult);
            fixture.detectChanges();

            const metadata: HTMLElement = fixture.nativeElement.querySelector('.result-metadata');
            expect(metadata.textContent).toContain('global.search.results.video');
        });

        it('should NOT render the location line for a non-content result', () => {
            fixture.componentRef.setInput('result', {
                id: '1',
                title: 'Test Exercise',
                type: 'exercise',
                metadata: { courseName: 'Test Course' },
            } as GlobalSearchResult);
            fixture.detectChanges();

            expect(component['isLectureContent']()).toBe(false);
            const metadata: HTMLElement = fixture.nativeElement.querySelector('.result-metadata');
            expect(metadata.textContent).toContain('Test Course');
            expect(metadata.textContent).not.toContain('global.search.pageAbbreviation');
            expect(metadata.textContent).not.toContain('global.search.results.video');
        });
    });
});

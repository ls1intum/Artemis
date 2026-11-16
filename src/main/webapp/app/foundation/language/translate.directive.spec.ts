import { inputBinding, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TranslateDirective', () => {
    let translateService: TranslateService;
    let spy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });

        translateService = TestBed.inject(TranslateService);
        spy = vi.spyOn(translateService, 'get');
    });

    // Create the fixture inside each test: Angular flushes all pending effects application-wide on a single
    // detectChanges(), so a fixture created in beforeEach would leak its translation effect into the other tests.
    function createWithKey(key: () => string | undefined) {
        return TestBed.createDirective(TranslateDirective, { tagName: 'div', bindings: [inputBinding('jhiTranslate', key)] });
    }

    it('should change HTML', () => {
        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it.each([undefined, ''])('should not call translateService.get for an empty key (%p) and should clear the element', (key) => {
        const dynamicFixture = createWithKey(() => key);
        const element = dynamicFixture.nativeElement;
        element.textContent = 'stale';

        // ngx-translate's get() throws synchronously on an empty key; the guard must prevent the call entirely
        expect(() => dynamicFixture.detectChanges()).not.toThrow();
        expect(spy).not.toHaveBeenCalled();
        expect(element.textContent).toBe('');
    });

    it('should translate once a previously empty key becomes non-empty', () => {
        const key = signal<string | undefined>(undefined);
        const dynamicFixture = createWithKey(key);
        dynamicFixture.detectChanges();
        expect(spy).not.toHaveBeenCalled();

        key.set('test');
        dynamicFixture.detectChanges();
        expect(spy).toHaveBeenCalledWith('test', undefined);
    });

    it('sanitizes the translated value before rendering (XSS via translateValues)', () => {
        // Simulate a translation whose interpolated translateValues carry user-controlled markup (e.g. a course
        // title). ngx-translate does not HTML-escape interpolation params, so the directive must sanitize.
        spy.mockReturnValue(of('Course <img src="x" onerror="alert(1)"><script>alert(2)</script> title') as ReturnType<typeof translateService.get>);

        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();
        const element = fixture.nativeElement;

        expect(element.innerHTML).not.toContain('onerror');
        expect(element.innerHTML).not.toContain('<script');
        expect(element.innerHTML).not.toContain('alert(2)');
        // benign text around the stripped payload is preserved
        expect(element.textContent).toContain('Course');
    });

    it('preserves benign inline markup in the translated value', () => {
        spy.mockReturnValue(of('Click <a href="/x"><strong>here</strong></a>') as ReturnType<typeof translateService.get>);

        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();
        const element = fixture.nativeElement;

        expect(element.innerHTML).toContain('<strong>here</strong>');
        expect(element.innerHTML).toContain('href="/x"');
    });

    function render(value: string): HTMLElement {
        spy.mockReturnValue(of(value) as ReturnType<typeof translateService.get>);
        const fixture = createWithKey(() => 'test');
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    it.each([
        ['an image', 'Text <img src="https://example.com/a.png" alt="x"> end', '<img'],
        ['a form', 'Text <form action="https://example.com"><input name="a"><button>Go</button></form> end', '<form'],
        ['an input', 'Text <input type="text" value="a"> end', '<input'],
        ['a style element', 'Text <style>p { color: red }</style> end', '<style'],
        ['an inline frame', 'Text <iframe src="https://example.com"></iframe> end', '<iframe'],
        ['a vector graphic', 'Text <svg><circle r="1"></circle></svg> end', '<svg'],
        ['a style attribute', 'Text <strong style="color:red;">bold</strong> end', 'style='],
        ['an event attribute', 'Text <strong onclick="doIt()">bold</strong> end', 'onclick'],
        ['a data attribute', 'Text <strong data-value="1">bold</strong> end', 'data-value'],
        ['a script address', 'Text <a href="javascript:doIt()">link</a> end', 'javascript:'],
    ])('removes %s from the translated value but keeps the text', (_name, value, removed) => {
        const element = render(value);

        expect(element.innerHTML).not.toContain(removed);
        expect(element.textContent).toContain('Text');
        expect(element.textContent).toContain('end');
    });

    it('keeps the markup that the translations use', () => {
        const element = render(
            '<strong class="text-state-danger">a</strong><b>b</b><i>c</i><em>d</em><code>e</code><br>' +
                '<span class="font-italic text-info">f</span><p class="text-primary small">g</p>' +
                '<a href="https://example.com/page" target="_blank" rel="noopener">h</a>',
        );

        expect(element.querySelector('strong.text-state-danger')?.textContent).toBe('a');
        expect(element.querySelector('b')?.textContent).toBe('b');
        expect(element.querySelector('i')?.textContent).toBe('c');
        expect(element.querySelector('em')?.textContent).toBe('d');
        expect(element.querySelector('code')?.textContent).toBe('e');
        expect(element.querySelector('br')).not.toBeNull();
        expect(element.querySelector('span.font-italic.text-info')?.textContent).toBe('f');
        expect(element.querySelector('p.text-primary.small')?.textContent).toBe('g');
        const link = element.querySelector('a');
        expect(link?.getAttribute('href')).toBe('https://example.com/page');
        expect(link?.getAttribute('target')).toBe('_blank');
        expect(link?.getAttribute('rel')).toBe('noopener');
    });

    it('keeps the text of placeholders that look like elements', () => {
        const element = render('Format: <participationId>-<login> and <bold>name</bold>');

        expect(element.textContent).toContain('name');
        expect(element.textContent).toContain('Format:');
    });

    it('shows string values that are inserted into a translation as text', () => {
        const fixture = TestBed.createDirective(TranslateDirective, {
            tagName: 'div',
            bindings: [inputBinding('jhiTranslate', () => 'test'), inputBinding('translateValues', () => ({ title: '<span class="x">Exam</span> & "more"', count: 3 }))],
        });
        fixture.detectChanges();

        expect(spy).toHaveBeenCalledWith('test', { title: '&lt;span class=&quot;x&quot;&gt;Exam&lt;/span&gt; &amp; &quot;more&quot;', count: 3 });
    });

    it('does not change the markup of the translation itself', () => {
        const element = render('Hello <strong class="text-state-danger">world</strong>');

        expect(element.querySelector('strong.text-state-danger')?.textContent).toBe('world');
    });
});

describe('TranslateDirective with translation interpolation', () => {
    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideTranslateService()] });
        TestBed.inject(TranslateService).use('en');
    });

    function render(translation: string, values: Record<string, unknown>): HTMLElement {
        TestBed.inject(TranslateService).setTranslation('en', { test: translation });
        const fixture = TestBed.createDirective(TranslateDirective, {
            tagName: 'div',
            bindings: [inputBinding('jhiTranslate', () => 'test'), inputBinding('translateValues', () => values)],
        });
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    it.each([
        ['a direct value', '{{ title }}', { title: '<a href="https://example.com">Title</a> & more' }],
        ['a nested property', '{{ course.title }}', { course: { title: '<a href="https://example.com">Title</a> & more' } }],
        ['an array item', '{{ titles.0 }}', { titles: ['<a href="https://example.com">Title</a> & more'] }],
        ['an interpolated array', '{{ titles }}', { titles: ['<a href="https://example.com">Title</a> & more'] }],
    ])('renders user markup in %s as literal text', (_name, translation, values) => {
        const element = render(translation, values);

        expect(element.textContent).toBe('<a href="https://example.com">Title</a> & more');
        expect(element.querySelector('a')).toBeNull();
    });

    it('preserves trusted translation formatting around escaped nested values', () => {
        const element = render('Read <a href="/help"><strong>{{ course.title }}</strong></a>', { course: { title: '<b>Title</b> & details' } });

        expect(element.querySelector('a')?.getAttribute('href')).toBe('/help');
        expect(element.querySelector('strong')?.textContent).toBe('<b>Title</b> & details');
        expect(element.querySelector('b')).toBeNull();
    });

    it('preserves scalar interpolation values and leaves the original parameters unchanged', () => {
        const values = { course: { title: 'A & B' }, counts: [3, 0], enabled: false, empty: null };
        const element = render('{{ course.title }}: {{ counts }}; {{ enabled }}; {{ empty }}', values);

        expect(element.textContent).toBe('A & B: 3, 0; false; null');
        expect(values).toEqual({ course: { title: 'A & B' }, counts: [3, 0], enabled: false, empty: null });
    });

    it('preserves the interpolation behavior of dates and custom value objects', () => {
        class Label {
            toString(): string {
                return 'A & B';
            }
        }
        const date = new Date('2024-01-15T10:30:00Z');
        const element = render('{{ date }} / {{ label }}', { date, label: new Label() });

        expect(element.textContent).toBe(`${date.toString()} / A & B`);
    });

    it('escapes nested properties without recursing indefinitely through cyclic parameters', () => {
        const course: Record<string, unknown> = { title: '<b>Title</b>' };
        course.self = course;
        const element = render('{{ course.self.title }}', { course });

        expect(element.textContent).toBe('<b>Title</b>');
        expect(element.querySelector('b')).toBeNull();
        expect(course.title).toBe('<b>Title</b>');
        expect(course.self).toBe(course);
    });
});

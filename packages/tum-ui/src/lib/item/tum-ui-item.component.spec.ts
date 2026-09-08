import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TumUiItemGroupComponent, TumUiItemGroupDirective } from './tum-ui-item-group.component';
import {
    TumUiItemActionsComponent,
    TumUiItemContentComponent,
    TumUiItemDescriptionComponent,
    TumUiItemMediaComponent,
    TumUiItemTitleComponent,
} from './tum-ui-item-parts.component';
import { TumUiItemComponent, TumUiItemDirective } from './tum-ui-item.component';

@Component({
    imports: [
        TumUiItemGroupComponent,
        TumUiItemComponent,
        TumUiItemMediaComponent,
        TumUiItemContentComponent,
        TumUiItemTitleComponent,
        TumUiItemDescriptionComponent,
        TumUiItemActionsComponent,
    ],
    template: `
        <tum-ui-item-group ariaLabel="Changed files" [size]="groupSize()" [separators]="separators()">
            <tum-ui-item>
                <tum-ui-item-media variant="icon"><span class="glyph">■</span></tum-ui-item-media>
                <tum-ui-item-content>
                    <tum-ui-item-title>src/Sorter.java</tum-ui-item-title>
                    <tum-ui-item-description>Modified two minutes ago</tum-ui-item-description>
                </tum-ui-item-content>
                <tum-ui-item-actions><button type="button">Open</button></tum-ui-item-actions>
            </tum-ui-item>
            <tum-ui-item size="large" variant="outline" class="overridden">Second</tum-ui-item>
        </tum-ui-item-group>
    `,
})
class GroupHostComponent {
    readonly groupSize = signal<'small' | 'medium' | 'large'>('small');
    readonly separators = signal(false);
}

describe('TumUiItemGroupComponent', () => {
    let fixture: ComponentFixture<GroupHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [GroupHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(GroupHostComponent);
        fixture.detectChanges();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    function items(): HTMLElement[] {
        return fixture.debugElement.queryAll(By.css('tum-ui-item')).map((debugElement) => debugElement.nativeElement as HTMLElement);
    }

    it('keeps the list semantics a flex container loses, and names the list', () => {
        const group = element('tum-ui-item-group');
        expect(group.getAttribute('role')).toBe('list');
        expect(group.getAttribute('aria-label')).toBe('Changed files');
        expect(group.getAttribute('data-slot')).toBe('item-group');
        expect(items().every((item) => item.getAttribute('role') === 'listitem')).toBe(true);
    });

    it('publishes one size to every row that does not override it', () => {
        expect(items()[0].getAttribute('data-size')).toBe('small');
        expect(items()[1].getAttribute('data-size')).toBe('large');

        fixture.componentInstance.groupSize.set('large');
        fixture.detectChanges();
        expect(items()[0].getAttribute('data-size')).toBe('large');
        expect(items()[1].getAttribute('data-size')).toBe('large');
    });

    it('draws separators as a group setting, so nothing that is not a row lands inside the list', () => {
        const group = element('tum-ui-item-group');
        expect(group.getAttribute('data-separators')).toBeNull();

        fixture.componentInstance.separators.set(true);
        fixture.detectChanges();
        expect(group.getAttribute('data-separators')).toBe('true');
        expect(group.className).toContain('tum:divide-y');
        expect(group.children).toHaveLength(2);
    });

    it('reflects the variant and keeps a consumer class alongside its own', () => {
        expect(items()[1].getAttribute('data-variant')).toBe('outline');
        expect(items()[1].classList).toContain('overridden');
    });

    it('publishes a slot for every part', () => {
        expect(element('tum-ui-item').getAttribute('data-slot')).toBe('item');
        expect(element('tum-ui-item-media').getAttribute('data-slot')).toBe('item-media');
        expect(element('tum-ui-item-content').getAttribute('data-slot')).toBe('item-content');
        expect(element('tum-ui-item-title').getAttribute('data-slot')).toBe('item-title');
        expect(element('tum-ui-item-description').getAttribute('data-slot')).toBe('item-description');
        expect(element('tum-ui-item-actions').getAttribute('data-slot')).toBe('item-actions');
    });

    it('lets the title truncate before an action is pushed off the row', () => {
        expect(element('tum-ui-item-content').className).toContain('tum:min-w-0');
        expect(element('tum-ui-item-title').className).toContain('tum:truncate');
        expect(element('tum-ui-item-actions').className).toContain('tum:shrink-0');
    });
});

@Component({
    imports: [TumUiItemGroupDirective, TumUiItemDirective],
    template: `
        <ul tumUiItemGroup ariaLabel="Recent runs" size="small">
            <li><a tumUiItem href="/one">First run</a></li>
            <li tumUiItem>Second run</li>
            <li><div tumUiItem role="presentation">Third run</div></li>
        </ul>
    `,
})
class DirectiveHostComponent {}

describe('TumUiItemDirective', () => {
    let fixture: ComponentFixture<DirectiveHostComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [DirectiveHostComponent] }).compileComponents();
        fixture = TestBed.createComponent(DirectiveHostComponent);
        fixture.detectChanges();
    });

    function element(selector: string): HTMLElement {
        return fixture.debugElement.query(By.css(selector)).nativeElement as HTMLElement;
    }

    it('turns a native list into a group without touching its markup', () => {
        const list = element('ul');
        expect(list.getAttribute('role')).toBe('list');
        expect(list.getAttribute('aria-label')).toBe('Recent runs');
        expect(list.getAttribute('data-slot')).toBe('item-group');
    });

    it('never overwrites an interactive element’s own role, so a row that is a link stays a link', () => {
        const link = element('a[tumUiItem]');
        expect(link.getAttribute('role')).toBeNull();
        expect(link.getAttribute('data-slot')).toBe('item');
        // A link row is the one form that gets hover and focus affordances, because it is the one that can act.
        expect(link.className).toContain('tum:cursor-pointer');
    });

    it('restores the listitem role flex layout strips from a non-interactive host', () => {
        expect(element('li[tumUiItem]').getAttribute('role')).toBe('listitem');
        expect(element('li[tumUiItem]').className).not.toContain('tum:cursor-pointer');
    });

    it('carries a consumer-supplied role forward rather than replacing it', () => {
        expect(element('div[tumUiItem]').getAttribute('role')).toBe('presentation');
    });

    it('shares the group size through the same context the component form uses', () => {
        expect(element('a[tumUiItem]').getAttribute('data-size')).toBe('small');
    });
});

describe('TumUiItemComponent (standalone)', () => {
    it('renders without a group, falling back to the medium size and claiming no list semantics', async () => {
        // `role="listitem"` with no `list` ancestor is an ARIA violation, so a row used on its own is a plain
        // element. Being usable outside a group is the point: it is also a summary row and a header line.
        await TestBed.configureTestingModule({ imports: [TumUiItemComponent] }).compileComponents();
        const fixture = TestBed.createComponent(TumUiItemComponent);
        const host = fixture.nativeElement as HTMLElement;
        fixture.detectChanges();
        expect(host.getAttribute('data-size')).toBe('medium');
        expect(host.getAttribute('role')).toBeNull();
        expect(host.getAttribute('data-slot')).toBe('item');
    });
});

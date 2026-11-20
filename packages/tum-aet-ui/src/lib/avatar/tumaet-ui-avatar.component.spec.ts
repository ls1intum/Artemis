import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TumAetUiAvatarComponent } from './tumaet-ui-avatar.component';

describe('TumAetUiAvatarComponent', () => {
    let fixture: ComponentFixture<TumAetUiAvatarComponent>;
    const host = () => fixture.nativeElement as HTMLElement;
    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [TumAetUiAvatarComponent] }).compileComponents();
        fixture = TestBed.createComponent(TumAetUiAvatarComponent);
        fixture.componentRef.setInput('name', 'Ada Lovelace');
        fixture.detectChanges();
    });
    it('provides a named image role with initials when no picture exists', () => {
        expect(host().getAttribute('role')).toBe('img');
        expect(host().getAttribute('aria-label')).toBe('Ada Lovelace');
        expect(host().textContent?.trim()).toBe('AL');
        expect(host().querySelector('img')).toBeNull();
    });
    it('renders a supplied image without repeating its accessible identity', () => {
        fixture.componentRef.setInput('imageUrl', '/portrait.png');
        fixture.detectChanges();
        const image = host().querySelector('img')!;
        expect(image.getAttribute('src')).toBe('/portrait.png');
        expect(image.alt).toBe('');
        expect(host().getAttribute('aria-label')).toBe('Ada Lovelace');
    });
    it('uses initials after a failed image and retries when the URL changes', async () => {
        fixture.componentRef.setInput('imageUrl', '/missing.png');
        fixture.detectChanges();
        host().querySelector('img')!.dispatchEvent(new Event('error'));
        fixture.detectChanges();
        expect(host().querySelector('img')).toBeNull();
        expect(host().textContent?.trim()).toBe('AL');
        fixture.componentRef.setInput('imageUrl', '/replacement.png');
        fixture.detectChanges();
        await fixture.whenStable();
        expect(host().querySelector('img')?.getAttribute('src')).toBe('/replacement.png');
    });
    it.each([
        ['', '?'],
        ['Ada', 'AD'],
        ['Änne Öztürk', 'ÄÖ'],
        ['李小龙', '李小'],
        ['Alex -', 'AL'],
        ['-- Ada - Lovelace --', 'AL'],
        ['123 ---', '?'],
        ['\u0301 \u0308', '?'],
        ['👩‍💻 Änne Öztürk', 'ÄÖ'],
        ['A\u0301da O\u0308ztürk', 'A\u0301O\u0308'],
        ['Supercalifragilisticexpialidocious Person', 'SP'],
    ])('handles the name %s with bounded Unicode initials', (name, initials) => {
        fixture.componentRef.setInput('name', name);
        fixture.detectChanges();
        expect(host().textContent?.trim()).toBe(initials);
        expect(host().getAttribute('aria-label')).toBe(name || 'Unknown person');
    });
    it('hides decorative avatars from assistive technology', () => {
        fixture.componentRef.setInput('decorative', true);
        fixture.detectChanges();
        expect(host().getAttribute('aria-hidden')).toBe('true');
        expect(host().getAttribute('aria-label')).toBeNull();
        expect(host().getAttribute('role')).toBeNull();
        expect(host().hasAttribute('tabindex')).toBe(false);
    });
});

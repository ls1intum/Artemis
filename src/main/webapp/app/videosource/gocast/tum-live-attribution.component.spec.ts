import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TumLiveAttributionComponent } from './tum-live-attribution.component';
import { MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

describe('TumLiveAttributionComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<TumLiveAttributionComponent>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            imports: [TumLiveAttributionComponent],
            providers: [],
        }).overrideComponent(TumLiveAttributionComponent, {
            set: {
                imports: [MockPipe(ArtemisTranslatePipe, (key: string) => key)],
            },
        });

        await TestBed.compileComponents();

        fixture = TestBed.createComponent(TumLiveAttributionComponent);
    });

    it('renders a link to the provided watch URL', async () => {
        fixture.componentRef.setInput('watchUrl', 'https://tum.live/w/eidi/1234');
        fixture.detectChanges();
        await fixture.whenStable();

        const link = fixture.nativeElement.querySelector('a.tum-live-attribution__link') as HTMLAnchorElement;
        expect(link).not.toBeNull();
        expect(link.getAttribute('href')).toBe('https://tum.live/w/eidi/1234');
    });

    it('opens the link in a new tab with noopener noreferrer', async () => {
        fixture.componentRef.setInput('watchUrl', 'https://tum.live/w/eidi/1234');
        fixture.detectChanges();
        await fixture.whenStable();

        const link = fixture.nativeElement.querySelector('a.tum-live-attribution__link') as HTMLAnchorElement;
        expect(link.getAttribute('target')).toBe('_blank');
        expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    });

    it('displays the attribution text via the i18n key', async () => {
        fixture.componentRef.setInput('watchUrl', 'https://tum.live/w/eidi/1234');
        fixture.detectChanges();
        await fixture.whenStable();

        const link = fixture.nativeElement.querySelector('a.tum-live-attribution__link') as HTMLAnchorElement;
        // MockPipe returns the key itself
        expect(link.textContent?.trim()).toBe('artemisApp.gocast.player.poweredByTumLive');
    });

    it('updates the link when watchUrl changes', async () => {
        fixture.componentRef.setInput('watchUrl', 'https://tum.live/w/foo/1');
        fixture.detectChanges();
        await fixture.whenStable();

        fixture.componentRef.setInput('watchUrl', 'https://tum.live/w/bar/2');
        fixture.detectChanges();
        await fixture.whenStable();

        const link = fixture.nativeElement.querySelector('a.tum-live-attribution__link') as HTMLAnchorElement;
        expect(link.getAttribute('href')).toBe('https://tum.live/w/bar/2');
    });
});

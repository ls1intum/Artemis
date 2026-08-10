import { Component, ElementRef, HostListener, inject, input, signal } from '@angular/core';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/foundation/util/fullscreen.util';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TumUiButtonDirective, TumUiTooltipDirective } from '@tumaet/ui-angular';

@Component({
    selector: 'jhi-fullscreen',
    templateUrl: './fullscreen.component.html',
    styleUrls: ['./fullscreen.scss'],
    imports: [TumUiButtonDirective, TumUiTooltipDirective, FaIconComponent, ArtemisTranslatePipe],
})
export class FullscreenComponent {
    private readonly fullScreenWrapper = inject(ElementRef);

    readonly position = input<'top-left' | 'top-right' | 'bottom-left' | 'bottom-right'>('top-right');

    readonly mode = input<'compact' | 'extended'>('extended');
    readonly showButton = input(true);
    /** Public so a host can render its own chrome-integrated control. */
    readonly fullscreenActive = signal(isFullScreen());

    protected readonly faCompress = faCompress;

    toggleFullscreen(): void {
        if (this.isFullScreen()) {
            exitFullscreen();
        } else {
            const element: HTMLElement = this.fullScreenWrapper.nativeElement;
            enterFullscreen(element);
        }
    }

    isFullScreen(): boolean {
        return isFullScreen();
    }

    @HostListener('document:fullscreenchange')
    protected updateFullscreenState(): void {
        this.fullscreenActive.set(isFullScreen());
    }
}

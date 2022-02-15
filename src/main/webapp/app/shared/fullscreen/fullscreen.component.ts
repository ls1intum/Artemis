import { Component, ElementRef, Input } from '@angular/core';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';

@Component({
    selector: 'jhi-fullscreen',
    templateUrl: './fullscreen.component.html',
    styleUrls: ['./fullscreen.scss'],
})
export class FullscreenComponent {
    @Input()
    position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right' = 'top-right';

    @Input()
    mode: 'compact' | 'extended' = 'extended';

    // Icons
    faCompress = faCompress;

    constructor(private fullScreenWrapper: ElementRef) {}

    /**
     * check current state and toggle fullscreen
     */
    toggleFullscreen() {
        if (this.isFullScreen()) {
            exitFullscreen();
        } else {
            const element: any = this.fullScreenWrapper.nativeElement;
            enterFullscreen(element);
        }
    }

    isFullScreen() {
        return isFullScreen();
    }
}

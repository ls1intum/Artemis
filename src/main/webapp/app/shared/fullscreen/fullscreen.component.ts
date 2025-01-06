import { Component, ElementRef, Input, inject } from '@angular/core';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from '../pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-fullscreen',
    templateUrl: './fullscreen.component.html',
    styleUrls: ['./fullscreen.scss'],
    imports: [NgbTooltip, FaIconComponent, ArtemisTranslatePipe],
})
export class FullscreenComponent {
    private fullScreenWrapper = inject(ElementRef);

    @Input()
    position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right' = 'top-right';

    @Input()
    mode: 'compact' | 'extended' = 'extended';

    // Icons
    faCompress = faCompress;

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

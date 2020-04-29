import { Component, OnInit, ElementRef, Input } from '@angular/core';

@Component({
    selector: 'jhi-fullscreen',
    templateUrl: './fullscreen.component.html',
    styleUrls: ['./fullscreen.scss'],
})
export class FullscreenComponent implements OnInit {
    buttonIcon = 'compress';

    @Input()
    position: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right' = 'top-right';

    @Input()
    mode: 'compact' | 'extended' = 'extended';

    constructor(private fullScreenWrapper: ElementRef) {}

    ngOnInit(): void {}

    /**
     * enter full screen
     */
    private enterFullscreen() {
        const element: any = this.fullScreenWrapper.nativeElement;
        // requestFullscreen for different browser types
        if (element.requestFullscreen) {
            element.requestFullscreen();
        } else if (element.mozRequestFullScreen) {
            element.mozRequestFullScreen();
        } else if (element.msRequestFullscreen) {
            element.msRequestFullscreen();
        } else if (element.webkitRequestFullscreen) {
            element.webkitRequestFullscreen();
        }
    }

    /**
     * exit fullscreen
     */
    private exitFullscreen() {
        const docElement = document as any;
        // exit fullscreen for different browser types
        if (document.exitFullscreen) {
            document.exitFullscreen();
        } else if (docElement.mozCancelFullScreen) {
            docElement.mozCancelFullScreen();
        } else if (docElement.msRequestFullscreen) {
            docElement.msRequestFullscreen();
        } else if (docElement.webkitExitFullscreen) {
            docElement.webkitExitFullscreen();
        }
    }

    /**
     * checks if this component is the current fullscreen component
     */
    isFullScreen() {
        const docElement = document as any;
        // check if this component is the current fullscreen component for different browser types
        if (docElement.fullscreenElement !== undefined) {
            return docElement.fullscreenElement;
        } else if (docElement.webkitFullscreenElement !== undefined) {
            return docElement.webkitFullscreenElement;
        } else if (docElement.mozFullScreenElement !== undefined) {
            return docElement.mozFullScreenElement;
        } else if (docElement.msFullscreenElement !== undefined) {
            return docElement.msFullscreenElement;
        }
    }

    /**
     * check current state and toggle fullscreen
     */
    toggleFullscreen() {
        if (this.isFullScreen()) {
            this.exitFullscreen();
        } else {
            this.enterFullscreen();
        }
    }
}

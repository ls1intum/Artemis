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

    private enterFullscreen() {
        const element: any = this.fullScreenWrapper.nativeElement;
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

    private exitFullscreen() {
        const docElement = document as any;
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

    isFullScreen() {
        const docElement = document as any;
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

    toggleFullscreen() {
        if (this.isFullScreen()) {
            this.exitFullscreen();
        } else {
            this.enterFullscreen();
        }
    }
}

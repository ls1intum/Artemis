import { Command } from 'app/markdown-editor/commands/command';

/**
 * Toggles fullscreen on button press.
 * Uses the markdown editor wrapper including tabs as element for fullscreen.
 *
 * The command needs to check different browser implementations of the fullscreen mode so it is handled correctly.
 */
export class FullscreenCommand extends Command {
    buttonIcon = 'compress';

    execute(input?: string): void {
        if (this.isFullScreen()) {
            this.exitFullscreen();
        } else {
            this.enterFullscreen();
        }
    }

    private enterFullscreen() {
        const element = this.markdownWrapper.nativeElement;
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

    private isFullScreen() {
        const docElement = document as any;
        if (docElement.fullscreenElement !== undefined) return docElement.fullscreenElement;
        if (docElement.webkitFullscreenElement !== undefined) return docElement.webkitFullscreenElement;
        if (docElement.mozFullScreenElement !== undefined) return docElement.mozFullScreenElement;
        if (docElement.msFullscreenElement !== undefined) return docElement.msFullscreenElement;
    }
}

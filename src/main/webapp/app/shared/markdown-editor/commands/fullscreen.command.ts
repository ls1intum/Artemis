import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCompress } from '@fortawesome/free-solid-svg-icons';
import { Command } from 'app/shared/markdown-editor/commands/command';
import { enterFullscreen, exitFullscreen, isFullScreen } from 'app/shared/util/fullscreen.util';

/**
 * Toggles fullscreen on button press.
 * Uses the markdown editor wrapper including tabs as element for fullscreen.
 *
 * The command needs to check different browser implementations of the fullscreen mode so it is handled correctly.
 */
export class FullscreenCommand extends Command {
    buttonIcon = faCompress as IconProp;
    buttonTranslationString = 'artemisApp.markdownEditor.commands.fullscreen';

    execute(): void {
        if (isFullScreen()) {
            exitFullscreen();
        } else {
            const element = this.markdownWrapper.nativeElement;
            enterFullscreen(element);
        }
    }
}

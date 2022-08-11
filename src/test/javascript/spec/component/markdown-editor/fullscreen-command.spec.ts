import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { FullscreenCommand } from 'app/shared/markdown-editor/commands/fullscreen.command';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import * as FullscreenUtil from 'app/shared/util/fullscreen.util';

describe('FullscreenCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, AceEditorModule, ArtemisMarkdownEditorModule],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should enable fullscreen on execute', () => {
        jest.spyOn(FullscreenUtil, 'enterFullscreen');
        jest.spyOn(FullscreenUtil, 'isFullScreen').mockReturnValue(false);

        const command = new FullscreenCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        command.execute();
        expect(FullscreenUtil.isFullScreen).toHaveBeenCalledOnce();
        expect(FullscreenUtil.enterFullscreen).toHaveBeenCalledOnce();
    });

    it('should disable fullscreen on execute', () => {
        jest.spyOn(FullscreenUtil, 'exitFullscreen');
        jest.spyOn(FullscreenUtil, 'isFullScreen').mockReturnValue(true);

        const command = new FullscreenCommand();
        comp.defaultCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();

        command.execute();
        expect(FullscreenUtil.isFullScreen).toHaveBeenCalledOnce();
        expect(FullscreenUtil.exitFullscreen).toHaveBeenCalledOnce();
    });
});

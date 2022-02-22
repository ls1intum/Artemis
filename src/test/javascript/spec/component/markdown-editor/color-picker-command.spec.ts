import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import { AceEditorModule } from 'app/shared/markdown-editor/ace-editor/ace-editor.module';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent, FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';
import { ArtemisTestModule } from '../../test.module';
import { ColorPickerCommand } from 'app/shared/markdown-editor/commands/colorPicker.command';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';

describe('ColorPickerCommand', () => {
    let comp: MarkdownEditorComponent;
    let fixture: ComponentFixture<MarkdownEditorComponent>;

    function testAddColor(hex: string, color: string) {
        const command = new ColorPickerCommand();
        comp.colorCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('test');

        command.execute(hex);
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('<span class="' + color + '">test</span>');
    }

    function testRemoveColor(color: string) {
        const command = new ColorPickerCommand();
        comp.colorCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('<span class="' + color + '">test</span>');

        command.execute('#ffffff');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, TranslateModule.forRoot(), AceEditorModule, ArtemisMarkdownEditorModule],
            declarations: [MockComponent(FaIconComponent), MockComponent(FaLayersComponent)],
            providers: [
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MarkdownEditorComponent);
                comp = fixture.componentInstance;
            });
    });

    const colorTable = [
        ['#ca2024', 'red'],
        ['#3ea119', 'green'],
        ['#ffffff', 'white'],
        ['#fffa5c', 'yellow'],
        ['#0d3cc2', 'blue'],
        ['#b05db8', 'lila'],
        ['#d86b1f', 'orange'],
    ];

    it.each(colorTable)('should add color %s on execute', (hex, color) => {
        testAddColor(hex, color);
    });

    it('should add color black on execute', () => {
        const command = new ColorPickerCommand();

        comp.colorCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('test');

        command.execute('#000000');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    });

    it.each(colorTable)('should remove color %s on execute', (hex, color) => {
        testRemoveColor(color);
    });
});

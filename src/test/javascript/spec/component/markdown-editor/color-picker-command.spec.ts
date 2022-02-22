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
        return TestBed.configureTestingModule({
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

    it('should add red color on execute', () => {
        testAddColor('#ca2024', 'red');
    });

    it('should add green color on execute', () => {
        testAddColor('#3ea119', 'green');
    });

    it('should add white color on execute', () => {
        testAddColor('#ffffff', 'white');
    });

    it('should add yellow color on execute', () => {
        testAddColor('#fffa5c', 'yellow');
    });

    it('should add blue color on execute', () => {
        testAddColor('#0d3cc2', 'blue');
    });

    it('should add lila color on execute', () => {
        testAddColor('#b05db8', 'lila');
    });

    it('should add orange color on execute', () => {
        testAddColor('#d86b1f', 'orange');
    });

    it('should add black color on execute', () => {
        const command = new ColorPickerCommand();

        comp.colorCommands = [command];
        fixture.detectChanges();
        comp.ngAfterViewInit();
        comp.aceEditorContainer.getEditor().setValue('test');

        command.execute('#000000');
        expect(comp.aceEditorContainer.getEditor().getValue()).toBe('test');
    });

    it('should remove green color on execute', () => {
        testRemoveColor('green');
    });

    it('should remove white color on execute', () => {
        testRemoveColor('white');
    });

    it('should remove orange color on execute', () => {
        testRemoveColor('orange');
    });

    it('should remove yellow color on execute', () => {
        testRemoveColor('yellow');
    });

    it('should remove red color on execute', () => {
        testRemoveColor('red');
    });

    it('should remove blue color on execute', () => {
        testRemoveColor('blue');
    });

    it('should remove lila color on execute', () => {
        testRemoveColor('lila');
    });
});

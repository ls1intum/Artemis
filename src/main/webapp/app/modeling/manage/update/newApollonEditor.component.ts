import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { ApollonEditor } from '@tumaet/apollon';

@Component({
    selector: 'diagram-wrapper',
    standalone: true,
    imports: [],
    template: ` <div #NewDiagramEditorComponent style="display: flex; flex-grow: 1"></div>`,
})
export class NewDiagramEditorComponent implements AfterViewInit, OnDestroy {
    @ViewChild('NewDiagramEditorComponent', { static: true }) container!: ElementRef;
    private editor?: ApollonEditor;

    ngAfterViewInit(): void {
        this.editor = new ApollonEditor(this.container.nativeElement);
    }
    ngOnDestroy(): void {
        this.editor?.dispose();
    }
}

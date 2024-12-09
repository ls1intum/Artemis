import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import isMobile from 'ismobilejs-es5';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { NgClass, NgStyle } from '@angular/common';
import { CdkDrag, CdkDragPlaceholder, CdkDragPreview } from '@angular/cdk/drag-drop';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FitTextDirective } from 'app/exercises/quiz/shared/fit-text/fit-text.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [NgClass, NgStyle, CdkDrag, ArtemisSharedModule, CdkDragPlaceholder, FitTextDirective, CdkDragPreview, TranslateDirective],
})
export class DragItemComponent implements OnInit {
    @Input() minWidth: string;
    @Input() dragItem: DragItem;
    @Input() clickDisabled: boolean;
    @Input() invalid: boolean;
    @Input() filePreviewPaths: Map<string, string> = new Map<string, string>();
    isMobile = false;

    constructor() {}

    /**
     * Initializes device information and whether the device is a mobile device
     */
    ngOnInit(): void {
        this.isMobile = isMobile(window.navigator.userAgent).any;
    }
}

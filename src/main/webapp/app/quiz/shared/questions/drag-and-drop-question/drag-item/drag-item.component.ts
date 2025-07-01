import { Component, Input, OnInit, ViewEncapsulation, input } from '@angular/core';
import isMobile from 'ismobilejs-es5';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { NgClass, NgStyle } from '@angular/common';
import { CdkDrag, CdkDragPlaceholder, CdkDragPreview } from '@angular/cdk/drag-drop';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { FitTextDirective } from 'app/quiz/shared/fit-text/fit-text.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, NgStyle, CdkDrag, SecuredImageComponent, CdkDragPlaceholder, FitTextDirective, CdkDragPreview, TranslateDirective],
})
export class DragItemComponent implements OnInit {
    readonly minWidth = input<string>(undefined!);
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input() dragItem: DragItem;
    readonly clickDisabled = input<boolean>(undefined!);
    readonly invalid = input<boolean>(undefined!);
    readonly filePreviewPaths = input<Map<string, string>>(new Map<string, string>());
    isMobile = false;

    /**
     * Initializes device information and whether the device is a mobile device
     */
    ngOnInit(): void {
        this.isMobile = isMobile(window.navigator.userAgent).any;
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

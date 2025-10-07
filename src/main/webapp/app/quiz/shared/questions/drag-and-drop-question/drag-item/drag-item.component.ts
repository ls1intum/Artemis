import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, ViewEncapsulation, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { NgClass, NgStyle } from '@angular/common';
import { CdkDrag, CdkDragPlaceholder, CdkDragPreview } from '@angular/cdk/drag-drop';
import { ImageComponent } from 'app/shared/image/image.component';
import { FitTextDirective } from 'app/quiz/shared/fit-text/fit-text.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { map } from 'rxjs';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, NgStyle, CdkDrag, ImageComponent, CdkDragPlaceholder, FitTextDirective, CdkDragPreview, TranslateDirective],
})
export class DragItemComponent {
    private breakpointObserver = inject(BreakpointObserver);
    readonly isMobile = toSignal(this.breakpointObserver.observe([Breakpoints.Handset]).pipe(map((result) => result.matches)), { initialValue: false });

    minWidth = input<string>();
    dragItem = input.required<DragItem>();
    clickDisabled = input<boolean>();
    invalid = input<boolean>();
    filePreviewPaths = input<Map<string, string>>(new Map<string, string>());

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}

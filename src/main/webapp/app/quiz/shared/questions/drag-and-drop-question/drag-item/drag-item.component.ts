import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, ViewEncapsulation, inject, input } from '@angular/core';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { NgClass, NgStyle } from '@angular/common';
import { CdkDrag, CdkDragPlaceholder, CdkDragPreview } from '@angular/cdk/drag-drop';
import { ImageComponent } from 'app/shared-ui/image/image.component';
import { FitTextDirective } from 'app/quiz/shared/fit-text/fit-text.directive';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { addPublicFilePrefix } from 'app/app.constants';
import { getIsMobileSignal } from 'app/foundation/util/global.utils';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html',
    styleUrls: ['./drag-item.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, NgStyle, CdkDrag, ImageComponent, CdkDragPlaceholder, FitTextDirective, CdkDragPreview, TranslateDirective],
})
export class DragItemComponent {
    private breakpointObserver = inject(BreakpointObserver);
    readonly isMobile = getIsMobileSignal(this.breakpointObserver);

    minWidth = input<string>();
    dragItem = input.required<DragItem>();
    clickDisabled = input<boolean>();
    invalid = input<boolean>();
    filePreviewPaths = input<Map<string, string>>(new Map<string, string>());
    // The owning drag-and-drop question id, needed to build the (question-scoped) drag item image URL. Drag item ids are only unique within their question.
    questionId = input<number>();

    protected readonly addPublicFilePrefix = addPublicFilePrefix;

    /**
     * Builds the image source for the drag item. A locally uploaded, not-yet-saved image is shown from its client-side preview (a data URL) if present; otherwise the saved picture is
     * served via the question-scoped file URL {@code files/drag-and-drop/questions/{questionId}/drag-items/{dragItemId}/{filename}}.
     *
     * `pictureFilePath` holds nothing but the filename, so this rebuild is what makes the picture reachable at all: a drag item id is only unique within its question, so the URL
     * that serves it is question-scoped. Taking the last segment rather than the whole value also covers a drag item saved while the server still stored an entire path.
     */
    protected imageSrc(): string | undefined {
        const picturePath = this.dragItem().pictureFilePath;
        if (!picturePath) {
            return undefined;
        }
        const preview = this.filePreviewPaths().get(picturePath);
        if (preview) {
            return addPublicFilePrefix(preview);
        }
        const questionId = this.questionId();
        const dragItemId = this.dragItem().id;
        if (questionId !== undefined && dragItemId !== undefined) {
            const filename = picturePath.substring(picturePath.lastIndexOf('/') + 1);
            return addPublicFilePrefix(`drag-and-drop/questions/${questionId}/drag-items/${dragItemId}/${filename}`);
        }
        return addPublicFilePrefix(picturePath);
    }
}

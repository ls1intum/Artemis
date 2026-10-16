import { Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/** The page frame of the pages that create or edit one content item: a spinner while loading, then the centered page. */
@Component({
    selector: 'jhi-lecture-unit-layout',
    templateUrl: './lecture-unit-layout.component.html',
    imports: [FaIconComponent, ArtemisTranslatePipe],
})
export class LectureUnitLayoutComponent {
    protected readonly faSpinner = faSpinner;

    isLoading = input<boolean>(false);
}

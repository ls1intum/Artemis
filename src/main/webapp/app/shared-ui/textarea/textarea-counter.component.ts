import { Component, inject, input } from '@angular/core';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-textarea-counter',
    templateUrl: './textarea-counter.component.html',
    providers: [],
    imports: [ArtemisTranslatePipe],
})
export class TextareaCounterComponent {
    private stringCountService = inject(StringCountService);

    maxLength = input<number>(0);
    content = input<string>();
    visible = input<boolean>();

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.content());
    }
}

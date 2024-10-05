import { Component, Input, inject } from '@angular/core';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';

@Component({
    selector: 'jhi-textarea-counter',
    templateUrl: './textarea-counter.component.html',
    providers: [],
})
export class TextareaCounterComponent {
    private stringCountService = inject(StringCountService);

    @Input() maxLength: number;
    @Input() content?: string;
    @Input() visible?: boolean;

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.content);
    }
}

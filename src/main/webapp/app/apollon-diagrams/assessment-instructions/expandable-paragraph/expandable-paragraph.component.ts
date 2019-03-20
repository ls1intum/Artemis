import {Component, Input, OnInit} from '@angular/core';

@Component({
    selector: 'jhi-expandable-paragraph',
    templateUrl: './expandable-paragraph.component.html',
    styleUrls: ['./expandable-paragraph.component.scss']
})
export class ExpandableParagraphComponent implements OnInit {
    @Input() header = 'Toggle paragraph';
    @Input() paragraph: string;
    @Input() isCollapsed = false;
    constructor() {
    }

    ngOnInit() {

    }
}

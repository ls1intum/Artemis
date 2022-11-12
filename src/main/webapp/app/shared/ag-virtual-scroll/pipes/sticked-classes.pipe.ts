import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
    name: 'stickedClasses',
})
export class StickedClassesPipe implements PipeTransform {
    private exceptionClasses: string[] = ['ag-virtual-scroll-odd', 'ag-virtual-scroll-even'];

    constructor() {}

    transform(classes: string) {
        if (classes) {
            const splitted = classes.includes(' ') ? classes.split(' ') : [classes];
            return splitted.filter((className) => !this.exceptionClasses.some((exc) => exc === className)).join(' ');
        }
        return '';
    }
}

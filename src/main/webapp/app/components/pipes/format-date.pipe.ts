import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({
   name: 'formatDate'
})
export class DatePipe implements PipeTransform {
   transform(date: any, args?: any): any {
     if (date == null || date === undefined) {
       return 'YYYY-MM-dd,HH:mm:ss';
     }
     const d = new Date(date);
     if (d && !isNaN(d.getTime())) {
       return moment(d).seconds(0).format('YYYY-MM-DD,HH:mm:ss');
     } else {
        return 'YYYY-MM-dd,HH:mm:ss';
     }
   }
}

import { Pipe, PipeTransform } from '@angular/core';
import * as moment from 'moment';

@Pipe({
   name: 'formatDate'
})
export class DatePipe implements PipeTransform {
   transform(date: any, args?: any): any {
     console.log(date);
     if (date && !isNaN(date)) {
       const d = new Date(date);
       return moment(d).seconds(0).format('YYYY-MM-DD,HH:mm:ss');
     } else {
        return 'YYYY-MM-dd,HH:mm:ss';
     }
     // yyyy-MM-ddTHH:mm:ss
   }
}

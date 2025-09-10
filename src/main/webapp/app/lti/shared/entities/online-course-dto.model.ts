import dayjs from 'dayjs/esm';
export class OnlineCourseDtoModel {
    public id?: number;
    public title?: string;
    public shortName?: string;
    public registrationId?: string;
    public startDate?: dayjs.Dayjs;
    public endDate?: dayjs.Dayjs;
    public description?: string;
    public numberOfStudents?: number;
}

import { AnnotationArray } from 'app/entities/annotation.model';

export const buildLogs = [
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '[ERROR] COMPILATION ERROR : ',
    },
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '[ERROR] /var/atlassian/application-data/bamboo/xml-data/build-dir/COURSEPROGSHORT-BASE-JOB1/assignment/src/todo/main/BubbleSort.java:[8,12] cannot find symbol',
    },
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '&nbsp; symbol:&nbsp; &nbsp;class voi',
    },
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '&nbsp; location: class todo.main.BubbleSort',
    },
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '[INFO] 1 error',
    },
];

export const extractedBuildLogErrors = {
    timestamp: 1557909131000,
    errors: { 'src/todo/main/BubbleSort.java': new AnnotationArray(...[{ type: 'error', row: 7, column: 11, text: 'cannot find symbol', ts: 1557909131000 }]) },
};

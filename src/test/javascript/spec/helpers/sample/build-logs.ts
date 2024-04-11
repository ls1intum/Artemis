import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';

export const buildLogs = [
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '[ERROR] COMPILATION ERROR : ',
    },
    {
        time: '2019-05-15T10:32:11+02:00',
        log: '[ERROR] /var/application-data/jenkins/xml-data/build-dir/COURSEPROGSHORT-BASE-JOB1/assignment/src/todo/main/BubbleSort.java:[8,12] cannot find symbol',
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

export const extractedBuildLogErrors = [
    {
        fileName: 'src/todo/main/BubbleSort.java',
        type: 'error',
        row: 7,
        column: 11,
        text: 'cannot find symbol',
        timestamp: 1557909131000,
    },
] as Array<Annotation>;

export const extractedErrorFiles = ['src/todo/main/BubbleSort.java'];

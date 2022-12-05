export const programmingExerciseProblemStatementC =
    '# Hello World\n' +
    '\n' +
    'In dieser Aufgabe werden Sie Ihr erstes C Programm erstellen.\n' +
    'Die Aufgabenstellung entnehmen Sie bitte [https://gbs.cm.in.tum.de](https://gbs.cm.in.tum.de).\n' +
    '\n' +
    '#### Allgemein\n' +
    '1. [task][Kompilieren](TestCompile)\n' +
    '2. [task][Rückgabewert == 0](TestReturnCode)\n' +
    '3. [task][Ausgabe prüfen](TestOutput)\n' +
    '\n' +
    '#### Address Sanitizer\n' +
    '1. [task][Kompilieren mit Address Sanitizer](TestCompileASan)\n' +
    '2. [task][Ausgabe prüfen mit Address Sanitizer](TestOutputASan)\n' +
    '\n' +
    '#### Undefined Behavior Sanitizer\n' +
    '1. [task][Kompilieren mit Undefined Behavior Sanitizer](TestCompileUBSan)\n' +
    '2. [task][Ausgabe prüfen mit Undefined Behavior Sanitizer](TestOutputUBSan)\n' +
    '\n' +
    '#### Leak Sanitizer\n' +
    '1. [task][Kompilieren mit Leak Sanitizer](TestCompileLeak)\n' +
    '2. [task][Ausgabe prüfen mit Leak Sanitizer](TestOutputLSan)\n' +
    '\n' +
    '#### GCC Static Analysis\n' +
    '1. [task][GCC Static analysis](TestGccStaticAnalysis)';

export const buildErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'helloWorld.c',
            fileContent: 'a',
        },
    ],
};

export const someSuccessfulErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'helloWorld.c',
            fileContent: '// Do magic ╰( ͡° ͜ʖ ͡° )つ──☆*:・ﾟ and implement your "Hello world" programm here.\n',
        },
    ],
};

export const allSuccessfulContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'helloWorld.c',
            fileContent:
                '#include <stdio.h> // For printf(...)\n' +
                '#include <stdlib.h> // For EXIT_SUCCESS\n' +
                '\n' +
                'int main(){\n' +
                '\tprintf("Hello world!\n");\n' +
                '\n' +
                '\treturn EXIT_SUCCESS; // Same as "return 0;"\n' +
                '}\n',
        },
    ],
};

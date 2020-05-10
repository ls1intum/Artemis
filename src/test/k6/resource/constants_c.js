export const programmingExerciseProblemStatementC =
    '### Tests\n' +
    '\n' +
    '#### General Tasks\n' +
    '1. [task][0 as Input](TestInput_0)\n' +
    '2. [task][1 as Input](TestInput_1)\n' +
    '3. [task][5 as Input](TestInput_5)\n' +
    '4. [task][7 as Input](TestInput_7)\n' +
    '5. [task][10 as Input](TestInput_10)\n' +
    '6. [task][Random Inputs](TestInputRandom_0, TestInputRandom_1, TestInputRandom_2, TestInputRandom_3, TestInputRandom_4)\n' +
    '\n' +
    '#### Address Sanitizer\n' +
    '1. [task][Address Sanitizer 1 as Input](TestInputASan_1)\n' +
    '2. [task][Address Sanitizer 5 as Input](TestInputASan_5)\n' +
    '\n' +
    '#### Leak Sanitizer\n' +
    '1. [task][Leak Sanitizer 1 as Input](TestInputLSan_1)\n' +
    '2. [task][Leak Sanitizer 5 as Input](TestInputLSan_5)\n' +
    '\n' +
    '#### Behaviour Sanitizer\n' +
    '1. [task][Undefined Behaviour Sanitizer 1 as Input](TestInputUBSan_1)\n' +
    '2. [task][Undefined Behaviour Sanitizer 5 as Input](TestInputUBSan_5)';

export const buildErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'main.c',
            fileContent: 'a',
        },
    ],
};

export const someSuccessfulErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'main.c',
            fileContent: 'int main(void) {\n' + '\treturn 0; // Success\n' + '}\n',
        },
    ],
};

export const allSuccessfulContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'main.c',
            fileContent:
                '#include <stdio.h> // For printf(...)\n' +
                '#include <unistd.h> // For fork()\n' +
                '#include <sys/wait.h> // For wait(...)\n' +
                '#include <string.h> // strcmp(...)\n' +
                '\n' +
                'int readProcessCount();\n' +
                'int createForks(int count);\n' +
                'void waitForEnd();\n' +
                'void waitPidChildren(int count);\n' +
                '\n' +
                'int main(void) {\n' +
                '\tint count = readProcessCount();\n' +
                '\n' +
                '\tint pid = createForks(count);\n' +
                '\tif(pid != 0) {\n' +
                '\t\twaitPidChildren(count);\n' +
                '\t\tprintf("Parent with PID %i terminated.\\n", getpid());\n' +
                '\t}\n' +
                '\treturn 0; // Success\n' +
                '}\n' +
                '\n' +
                'int readProcessCount() {\n' +
                '\tint count = -1;\n' +
                '\tdo\n' +
                '\t{\n' +
                '\t\tprintf("Enter process count:\\n");\n' +
                '\t\tfflush(stdout);\n' +
                '\t\tif(!scanf("%i", &count)) {\n' +
                '\t\t\t// Clear input if user did not enter a valid int:\n' +
                '\t\t\tint c;\n' +
                "\t\t\twhile ((c = getchar()) != '\\n' && c != EOF);\n" +
                '\t\t}\n' +
                '\t} while (count < 0);\n' +
                '\treturn count;\n' +
                '}\n' +
                '\n' +
                '// Recursively create new processes:\n' +
                'int createForks(int count) {\n' +
                '\tif(count <= 0) { return 1;}\n' +
                '\n' +
                '\tint pid = fork();\n' +
                '\tswitch(pid) {\n' +
                '\t\tcase -1: // Error\n' +
                '\t\t\tperror ("fork() failed");\n' +
                '\t\t\tbreak;\n' +
                '\n' +
                '\t\tcase 0: // Child\n' +
                '\t\t\tpid =  getpid();\n' +
                '\t\t\tprintf("I\'m your child! PID: %i, PPID: %i\\n", pid, getppid());\n' +
                '\t\t\twaitForEnd();\n' +
                '\t\t\tprintf("Child with PID %i terminated.\\n", pid);\n' +
                '\t\t\treturn 0;\n' +
                '\n' +
                '\t\tdefault: // Parent\n' +
                '\t\t\treturn createForks(--count);\n' +
                '\t}\n' +
                '\treturn pid;\n' +
                '}\n' +
                '\n' +
                'void waitForEnd() {\n' +
                '\tchar buffer[1024];\n' +
                '\tdo\n' +
                '\t{\n' +
                '\t\tif(!scanf("%s", buffer)) {\n' +
                '\t\t\t// Clear input if user did not enter a valid int:\n' +
                '\t\t\tint c;\n' +
                "\t\t\twhile ((c = getchar()) != '\\n' && c != EOF);\n" +
                '\t\t}\n' +
                '\t\t// printf("Read: %s\\n", buffer); // Debug output\n' +
                '\t} while (strcmp(buffer, "END"));\n' +
                '}\n' +
                '\n' +
                'void waitPidChildren(int count) {\n' +
                '\tfor(int i = 0; i < count; i++) {\n' +
                '\t\twaitpid(-1, NULL, 0); // Make sure we wait on the child process to prevent it from getting a Zombie process\n' +
                '\t}\n' +
                '}',
        },
    ],
};

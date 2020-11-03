export const programmingExerciseProblemStatementC = '### Tests\n' + '\n';
'#### Allgemein\n' +
    '1. [task][Kompilieren](TestCompile)\n' +
    '2. [task][Rot0 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutput_0)\n' +
    '3. [task][Rot1 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutput_1)\n' +
    '4. [task][Rot26 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutput_26)\n' +
    '5. [task][Rot1563 mit "eIxK4zuaarER" als Eingabe](TestOutput_1563)\n' +
    '6. [task][Zufällige Eingaben](TestOutputRandom_0, TestOutputRandom_1, TestOutputRandom_2, TestOutputRandom_3, TestOutputRandom_4, TestOutputRandom_5, TestOutputRandom_6, TestOutputRandom_7, TestOutputRandom_8, TestOutputRandom_9)\n' +
    '\n' +
    '#### Adress Sanitizer\n' +
    '1. [task][Adress Sanitizer Kompilieren](TestCompileASan)\n' +
    '2. [task][Adress Sanitizer Rot27 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutputASan)\n' +
    '\n' +
    '#### Undefined Behavior Sanitizer\n' +
    '1. [task][Undefined Behavior Sanitizer Kompilieren](TestCompileUBSan)\n' +
    '2. [task][Undefined Behavior Sanitizer Rot27 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutputUBSan)\n' +
    '\n' +
    '#### Leak Sanitizer\n' +
    '1. [task][Leak Sanitizer Kompilieren](TestCompileLeak)\n' +
    '2. [task][Leak Sanitizer Rot27 mit "aAbByYzZ123!%&/()Oau" als Eingabe](TestOutputLSan)\n' +
    '\n' +
    '#### GCC Static Analysis\n' +
    '1. [task][GCC Static analysis](TestGccStaticAnalysis)';

export const buildErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'rotX.c',
            fileContent: 'a',
        },
    ],
};

export const someSuccessfulErrorContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'rotX.c',
            fileContent: 'int main(void) {\n' + '\treturn 0; // Success\n' + '}\n',
        },
    ],
};

export const allSuccessfulContentC = {
    newFiles: [],
    content: [
        {
            fileName: 'rotX.c',
            fileContent:
                '#include <ctype.h> // isalpha(...), isupper(...)\n' +
                '#include <stdlib.h> // size_t\n' +
                '#include <unistd.h> // read(...)\n' +
                '#include <stdio.h> // printf(...)\n' +
                '#include <unistd.h> // sleep(...)\n' +
                '\n' +
                '#define MAX_BUFFER_SIZE 1024\n' +
                '\n' +
                'char rotX(char in, char rot);\n' +
                'char readRotCount();\n' +
                '\n' +
                'char rotX(char in, char rot) {\n' +
                '\tif(isalpha(in)) { // We only want to convert alphabet characters\n' +
                '\t\tif(isupper(in)) {\n' +
                "\t\t\treturn 'A' + ((in - 'A') + rot) % 26;\n" +
                '\t\t}\n' +
                "\t\treturn 'a' + ((in - 'a') + rot) % 26;\n" +
                '\t}\n' +
                '\treturn in;\n' +
                '}\n' +
                '\n' +
                'char readRotCount() {\n' +
                '\tint rot = -1;\n' +
                '\tdo\n' +
                '\t{   \n' +
                '\t\tprintf("Enter Rot:\\n");\n' +
                '\t\tfflush(stdout);\n' +
                '\t\tif(!scanf("%i", &rot)) {\n' +
                '\t\t\t// Clear input if user did not enter a valid int:\n' +
                '\t\t\tint c = 0;\n' +
                "\t\t\twhile ((c = getchar()) != '\\n' && c != EOF) {}\n" +
                '\t\t}\n' +
                '\t} while (rot < 0);\n' +
                '\treturn (char)(rot%26); // Perform modulo since it does not change the result\n' +
                '}\n' +
                '\n' +
                'int main() {\n' +
                '\tchar rot = readRotCount();\n' +
                '\tchar buff[MAX_BUFFER_SIZE];\n' +
                '\n' +
                '\tprintf("Enter text:\\n");\n' +
                "\t// Read MAX_BUFFER_SIZE - 1 chars. Don't forget about the '\0' at the end!\n" +
                '\tsize_t n = read(STDIN_FILENO, buff, MAX_BUFFER_SIZE - 1);\n' +
                "\tbuff[n] = '\\0'; // Ensure we terminate the string with '\\0'. Important for printing later.\n" +
                '\tfor (size_t i = 0; i < n && buff[i]; i++)\n' +
                '\t{\n' +
                '\t\t// Replace character by character:\n' +
                '\t\tbuff[i] = rotX(buff[i], rot);\n' +
                '\t}\n' +
                '\t// Print the result:\n' +
                '\tprintf("%s", buff);\n' +
                '\tflush(stdin); // Ensure we flush our output\n' +
                '\tsleep(1); // Sleep one second to prevent th output from not getting read sometimes by the tester\n' +
                '\t\n' +
                '}\n',
        },
    ],
};

#include <ctype.h> // isalpha(...), isupper(...)
#include <stdlib.h> // size_t
#include <unistd.h> // read(...)
#include <stdio.h> // printf(...)
#include <unistd.h> // sleep(...)

#define MAX_BUFFER_SIZE 1024

char rotX(char in, char rot);
char readRotCount();

char rotX(char in, char rot) {
    if(isalpha(in)) { // We only want to convert alphabet characters
        if(isupper(in)) {
            return 'A' + ((in - 'A') + rot) % 26;
        }
        return 'a' + ((in - 'a') + rot) % 26;
    }
    return in;
}

char readRotCount() {
    int rot = -1;
    do
    {   
        printf("Enter Rot:\n");
        fflush(stdout);
        if(!scanf("%i", &rot)) {
            // Clear input if user did not enter a valid int:
            int c  = 0;
            while ((c = getchar()) != '\n' && c != EOF) {};
        }
    } while (rot < 0);
    return (char)(rot%26); // Perform modulo since it does not change the result
}

int main() {
    char rot = readRotCount();
    char buff[MAX_BUFFER_SIZE];

    printf("Enter text:\n");
    // Read MAX_BUFFER_SIZE - 1 chars. Don't forget about the '\0' at the end!
    size_t n = read(STDIN_FILENO, buff, MAX_BUFFER_SIZE - 1);
    buff[n] = '\0'; // Ensure we terminate the string with '\0'. Important for printing later.
    for (size_t i = 0; i < n && buff[i]; i++)
    {
        // Replace character by character:
        buff[i] = rotX(buff[i], rot);
    }
    // Print the result:
    printf("%s", buff);
    fflush(stdin); // Ensure we flush our output
    sleep(1); // Sleep one second to prevent th output from not getting read sometimes by the tester
}
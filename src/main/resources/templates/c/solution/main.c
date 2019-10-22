#include <stdio.h> // For printf(...)
#include <unistd.h> // For fork()
#include <sys/wait.h> // For wait(...)
#include <string.h> // strcmp(...)

int readProcessCount();
int createForks(int count);
void waitForEnd();
void waitPidChildren(int count);

int main(void) {
    int count = readProcessCount();

    int pid = createForks(count);
    if(pid != 0) {
        waitPidChildren(count);
        printf("Parent with PID %i terminated.\n", getpid());
    }
    return 0; // Success
}

int readProcessCount() {
    int count = -1;
    do
    {
        printf("Enter process count:\n");
        fflush(stdout);
        if(!scanf("%i", &count)) {
            // Clear input if user did not enter a valid int:
            int c;
            while ((c = getchar()) != '\n' && c != EOF);
        }
    } while (count < 0);
    return count;
}

// Recursively create new processes:
int createForks(int count) {
    if(count <= 0) { return 1;}

    int pid = fork();
    switch(pid) {
        case -1: // Error
            perror ("fork() failed");
            break;

        case 0: // Child
            pid =  getpid();
            printf("I'm your child! PID: %i, PPID: %i\n", pid, getppid());
            waitForEnd();
            printf("Child with PID %i terminated.\n", pid);
            return 0;

        default: // Parent
            return createForks(--count);
    }
    return pid;
}

void waitForEnd() {
    char buffer[1024];
    do
    {
        if(!scanf("%s", buffer)) {
            // Clear input if user did not enter a valid int:
            int c;
            while ((c = getchar()) != '\n' && c != EOF);
        }
        // printf("Read: %s\n", buffer); // Debug output
    } while (strcmp(buffer, "END"));
}

void waitPidChildren(int count) {
    for(int i = 0; i < count; i++) {
        waitpid(-1, NULL, 0); // Make sure we wait on the child process to prevent it from getting a Zombie process
    }
}
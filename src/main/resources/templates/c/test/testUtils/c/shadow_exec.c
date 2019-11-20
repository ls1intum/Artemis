/**
 * Shadows all exec commands if compiled with
 **/

#include <stdio.h>

void printWarning() {
  printf("Access denied! This will be reported.\n");
}

int system(const char *command) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)command;

  printWarning();
  return -1;
}

int execl(const char *pathname, const char *arg, ... ) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)pathname;
  (void)arg;

  printWarning();
  return -1;
}

int execlp(const char *file, const char *arg, ...) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)file;
  (void)arg;

  printWarning();
  return -1;
}

int execle(const char *pathname, const char *arg, ...) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)pathname;
  (void)arg;

  printWarning();
  return -1;
}

int execv(const char *pathname, char *const argv[]) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)pathname;
  (void)argv;

  printWarning();
  return -1;
}

int execve(const char *path, char *const argv[], char *const envp[]) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)path;
  (void)argv;
  (void)envp;

  printWarning();
  return -1;
}

int execvp(const char *file, char *const argv[]) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)file;
  (void)argv;

  printWarning();
  return -1;
}

int execvpe(const char *file, char *const argv[], char *const envp[]) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)file;
  (void)argv;
  (void)envp;

  printWarning();
  return -1;
}

int fexecve(int fd, char *const argv[], char *const envp[]) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)fd;
  (void)argv;
  (void)envp;

  printWarning();
  return -1;
}

int execveat(int dirfd, const char *pathname, char *const argv[], char *const envp[], int flags) {
  // Since we do not use the data attribute this surpresses the
  // "warning: unused parameter ..." during compile time
  (void)dirfd;
  (void)pathname;
  (void)argv;
  (void)envp;
  (void)flags;

  printWarning();
  return -1;
}
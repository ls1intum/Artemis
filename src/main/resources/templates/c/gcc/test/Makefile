.PHONY: all run

all: helloWorld

#############################################################
# DON'T change the variable names of INCLUDEDIRS and SOURCE
#############################################################
# A list of include directories
INCLUDEDIRS =
# A list of source files
SOURCE = helloWorld.c

ifeq ($(origin CC),default)
CC = gcc
endif

# Warning flags
# For more information about gcc warnings: https://embeddedartistry.com/blog/2017/3/7/clang-weverything
# -Wall:		        Print warnings
# -Wextra:		        Enable additional warnings not covered by "-Wall"
# -Wpedantic:	        Reject everything that is not ISO C
# -g					Generates debug information to be used by GDB debugger
WFLAGS = -Wall -Wextra -Wpedantic -g

# Flags that only the sanitizer targets (asan, ubsan, lsan) receive.
# -DGBS_DISABLE_MALLOC_SHADOW  The tester's malloc shadow conflicts with the sanitizer allocator,
#                              so the shadow leaves malloc alone in these builds.
# -fno-pie -no-pie             Link at a fixed low load address instead of a randomised one. GCC's
#                              address and leak sanitizer runtimes reserve a fixed 4 TiB region at
#                              0x600000000000 (96 TiB) for their allocator, while a
#                              position-independent executable is loaded at 0x555555554000
#                              (85.33 TiB) plus up to ((2^vm.mmap_rnd_bits) - 1) << 12 bytes. On a
#                              host with vm.mmap_rnd_bits=32 - the kernel default since 6.5, so
#                              Ubuntu 24.04 among others - that offset reaches 16 TiB, which places
#                              the executable inside the reserved region in roughly one run out of
#                              four. The sanitizer then dies before main() in an endless loop of
#                              "AddressSanitizer:DEADLYSIGNAL" lines, printing no report and never
#                              exiting, which the tester can only report as a timeout. A non-PIE
#                              executable is loaded at 0x400000 and cannot collide at any entropy
#                              setting. libsanitizer made the allocator base dynamic in GCC 14; drop
#                              these two flags once the build image ships that or newer.
#                              See https://github.com/google/sanitizers/issues/1614
SANITIZER_FLAGS = -DGBS_DISABLE_MALLOC_SHADOW -fno-pie -no-pie

# Compile without sanitizers and disable optimisation
# $(SOURCE): 	        Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 			        Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:			        Do not optimize the program
# $(WFLAGS)             Warning flags
helloWorld: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS)

# Compile with address sanitizer enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:				Treat all warnings as errors
# -fsanitize=address:	Address sanitizer
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
asan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=address

# Compile with undefined behavior sanitizer enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:				Treat all warnings as errors
# -fsanitize=undefined:	Fast undefined behavior check
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
ubsan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=undefined

# Compile with leak sanitizer enabled
# $(SOURCE): 		    Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 				    Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:				    Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:			    Treat all warnings as errors
# -fsanitize=leak	    Basic memory leak sanitizer
#                       (https://gcc.gnu.org/onlinedocs/gcc-5.3.0/gcc/Debugging-Options.html#index-fsanitize_003dundefined-652)
lsan: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) $(SANITIZER_FLAGS) -Werror -fsanitize=leak

# Compile with GCC static analysis enabled
# $(SOURCE): 			Input file(s)
# $(INCLUDEDIRS:%=-I%)  Include directories
# -o: 					Link the resulting object files
# $@.out:	            Built-in variable representing the current target name + file extension .out
# -std=c11              Set C standard
# -O0:					Do not optimize the program
# $(WFLAGS)             Warning flags
# -Werror:				Treat all warnings as errors
# -fanalyzer:			GCC static analysis (for GCC >= 10.0 only)
#                       (https://developers.redhat.com/blog/2020/03/26/static-analysis-in-gcc-10/)
staticAnalysis: FORCE
	$(CC) $(SOURCE) $(INCLUDEDIRS:%=-I%) -o $@.out -std=c11 -O0 $(WFLAGS) -Werror -fanalyzer

# Execute the compiled program
run:
	./helloWorld.out

# Make sure we always rebuild
# Required for the tester
FORCE: ;

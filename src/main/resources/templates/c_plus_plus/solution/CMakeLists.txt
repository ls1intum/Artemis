cmake_minimum_required(VERSION 3.13)
project(ArtemisExercise)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(assignment src/sort.cpp)
target_include_directories(assignment PUBLIC include)

add_executable(assignment_main src/main.cpp)
target_link_libraries(assignment_main assignment)

#pragma once

#include <vector>

void selection_sort(std::vector<int>::iterator begin,
                    std::vector<int>::iterator end);

void insertion_sort(std::vector<int>::iterator begin,
                    std::vector<int>::iterator end);

void quicksort(std::vector<int>::iterator begin,
               std::vector<int>::iterator end);

void mergesort(std::vector<int>::iterator begin,
               std::vector<int>::iterator end);

void mergesort_inplace(std::vector<int>::iterator begin,
                       std::vector<int>::iterator end);

void heapsort(std::vector<int>::iterator begin, std::vector<int>::iterator end);

void heapsort_explicit(std::vector<int>::iterator begin,
                       std::vector<int>::iterator end);

void bogosort(std::vector<int>::iterator begin, std::vector<int>::iterator end);

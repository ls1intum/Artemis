#include "sort.hpp"

#include <algorithm>
#include <random>
#include <vector>

void selection_sort(std::vector<int>::iterator begin,
                    std::vector<int>::iterator end) {
  for (auto it = begin; it != end; ++it) {
    auto min = std::min_element(it, end);
    // std::iter_swap(min, it); // unstable
    std::rotate(it, min, min + 1);
  }
}

void insertion_sort(std::vector<int>::iterator begin,
                    std::vector<int>::iterator end) {
  for (auto it = begin; it != end; ++it) {
    auto insertion_pos = std::upper_bound(begin, it, *it);
    std::rotate(insertion_pos, it, it + 1);
  }
}

void quicksort(std::vector<int>::iterator begin,
               std::vector<int>::iterator end) {
  if (end - begin <= 1) {
    return;
  }
  auto pivot = *begin;
  auto middle =
      std::partition(begin + 1, end, [pivot](int i) { return i < pivot; });
  auto new_middle = std::rotate(begin, begin + 1, middle);
  quicksort(begin, new_middle);
  quicksort(new_middle + 1, end);
}

void mergesort(std::vector<int>::iterator begin,
               std::vector<int>::iterator end) {
  auto length = end - begin;
  if (length <= 1) {
    return;
  }
  std::vector<int> tmp(begin, end);
  auto middle = tmp.begin() + length / 2;
  mergesort(tmp.begin(), middle);
  mergesort(middle, tmp.end());
  std::merge(tmp.begin(), middle, middle, tmp.end(), begin);
}

void mergesort_inplace(std::vector<int>::iterator begin,
                       std::vector<int>::iterator end) {
  auto length = end - begin;
  if (length <= 1) {
    return;
  }
  auto middle = begin + length / 2;
  mergesort_inplace(begin, middle);
  mergesort_inplace(middle, end);
  std::inplace_merge(begin, middle, end);
}

void heapsort(std::vector<int>::iterator begin,
              std::vector<int>::iterator end) {
  std::make_heap(begin, end);
  std::sort_heap(begin, end);
}

void heapsort_explicit(std::vector<int>::iterator begin,
                       std::vector<int>::iterator end) {
  std::make_heap(begin, end);
  while (end != begin) {
    std::pop_heap(begin, end);
    --end;
  }
}

void bogosort(std::vector<int>::iterator begin,
              std::vector<int>::iterator end) {
  while (!std::is_sorted(begin, end)) {
    std::next_permutation(begin, end);
  }
}

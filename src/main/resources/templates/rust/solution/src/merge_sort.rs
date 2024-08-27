use std::cmp::Ord;

use crate::sort_strategy::SortStrategy;

pub struct MergeSort;

impl<T: Ord + Copy> SortStrategy<T> for MergeSort {
    /// Sorts items with the Merge Sort algorithm.
    ///
    /// Arguments:
    ///
    /// * `input`: slice of items to be sorted
    fn perform_sort(&self, input: &mut [T]) {
        mergesort(input);
    }
}

fn mergesort<T: Ord + Copy>(input: &mut [T]) {
    if input.len() < 2 {
        return;
    }

    let middle = input.len() / 2;
    let (left, right) = input.split_at_mut(middle);
    mergesort(left);
    mergesort(right);
    merge(input, middle);
}

fn merge<T: Ord + Copy>(input: &mut [T], middle: usize) {
    let mut result = Vec::with_capacity(input.len());

    let mut left_index = 0;
    let mut right_index = middle;

    while left_index < middle && right_index < input.len() {
        if input[left_index] <= input[right_index] {
            result.push(input[left_index]);
            left_index += 1;
        } else {
            result.push(input[right_index]);
            right_index += 1;
        }
    }

    if left_index < middle {
        while left_index < middle {
            result.push(input[left_index]);
            left_index += 1;
        }
    } else {
        while right_index < input.len() {
            result.push(input[right_index]);
            right_index += 1;
        }
    }

    input.copy_from_slice(&result);
}

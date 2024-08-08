use std::cmp::Ord;

use crate::sort_strategy::SortStrategy;

pub struct BubbleSort;

impl<T: Ord> SortStrategy<T> for BubbleSort {
    /// Sorts items with the Bubble Sort algorithm.
    ///
    /// Arguments:
    ///
    /// * `input`: slice of items to be sorted
    fn perform_sort(&self, input: &mut [T]) {
        let len = input.len();
        for i in (0..len).rev() {
            for j in 0..i {
                if input[j] > input[j + 1] {
                    input.swap(j, j + 1);
                }
            }
        }
    }
}

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
        input.sort();
    }
}

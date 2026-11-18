use crate::{
    bubble_sort::BubbleSort, context::Context, merge_sort::MergeSort, sort_strategy::SortStrategy,
};
use chrono::NaiveDate;
use std::cell::RefCell;

const SIZE_THRESHOLD: usize = 10;

pub struct Policy<'a> {
    context: &'a RefCell<Context>,
}

impl<'a> Policy<'a> {
    pub fn new(context: &'a RefCell<Context>) -> Policy<'a> {
        Policy { context }
    }

    /// Chooses a strategy depending on the number of items.
    pub fn configure(&mut self, data: &[NaiveDate]) {
        let sort_algorithm: Box<dyn SortStrategy<NaiveDate>> = if data.len() > SIZE_THRESHOLD {
            Box::new(MergeSort)
        } else {
            Box::new(BubbleSort)
        };

        self.context.borrow_mut().set_sort_algorithm(sort_algorithm);
    }
}

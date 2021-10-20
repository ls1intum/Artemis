//
//  Policy.swift
//  ${appName}
//

import Foundation

/*
 Task 2.3:
 Implement the Policy Class
*/
public class Policy {
    private var context: Context!

    init(_ context: Context!) {
        self.context = context
    }

    /// Chooses a strategy depending on the choice of the user.
    public func configure(sortAlgorithm: SortAlgorithm) {
        switch sortAlgorithm {
        case SortAlgorithm.MergeSort:
            self.context.setSortAlgorithm(MergeSort())
        case SortAlgorithm.BubbleSort:
            self.context.setSortAlgorithm(BubbleSort())
        }
    }
}

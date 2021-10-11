//
//  Policy.swift
//  ${appName}
//
//  Created by Daniel Kainz on 12.10.21.
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
    public func configure(sortAlgorithm: String) {
        switch sortAlgorithm {
        case "Merge Sort":
            self.context.setSortAlgorithm(MergeSort())
        case "Bubble Sort":
            self.context.setSortAlgorithm(BubbleSort())
        default:
            self.context.setSortAlgorithm(MergeSort())
        }
    }
}

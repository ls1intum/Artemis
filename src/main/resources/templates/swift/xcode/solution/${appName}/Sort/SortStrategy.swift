//
//  SortStrategy.swift
//  ${appName}
//
//  Created by Daniel Kainz on 12.10.21.
//

import Foundation

/*
 Task 2.1:
 Implement the SortStrategy Protocol
*/
public protocol SortStrategy {
    /**
      Sorts a list of Dates.

      - Parameter input: list of Dates
     */
    func performSort(_ input: [Date]) -> [Date]
}

//
//  Context.swift
//  ${appName}
//
//  Created by Daniel Kainz on 12.10.21.
//

import Foundation

/*
 Task 2.2:
 Implement the Context Class
*/
public class Context {
    /*
     We added some helper functions to make your life easier
    */
    
    /// Generates an Array of random Date objects with random Array size between 5 and 15.
    private func createRandomDatesList() -> [Date] {
        let listLength: Int = self.randomIntegerWithin(5, 15)
        var list = [Date]()
        let dateFormat = DateFormatter()
        dateFormat.dateFormat = "dd.MM.yyyy"
        dateFormat.timeZone = TimeZone(identifier: "UTC")
        let lowestDate: Date! = dateFormat.date(from: "08.11.2016")
        let highestDate: Date! = dateFormat.date(from: "15.04.2017")
        for _ in 0 ..< listLength {
            let randomDate: Date! = self.randomDateWithin(lowestDate, highestDate)
            list.append(randomDate)
        }
        return list
    }
    
    /// Creates a random Date within given Range
    private func randomDateWithin(_ low: Date, _ high: Date) -> Date {
        let randomSeconds: Double = self.randomDoubleWithin(low.timeIntervalSince1970, high.timeIntervalSince1970)
        return Date(timeIntervalSince1970: randomSeconds)
    }
    
    /// Creates a random Double within given Range
    private func randomDoubleWithin(_ low: Double, _ high: Double) -> Double {
        return Double.random(in: low...high)
    }
    
    /// Creates a random Integer within given Range
    private func randomIntegerWithin(_ low: Int, _ high: Int) -> Int {
        return Int.random(in: low...high)
    }
}

//
//  DateFormatterExtension.swift
//  ${appName}
//
//  Created by Daniel Kainz on 12.10.21.
//

import Foundation

extension DateFormatter {
    /// Returns the full date without the time.
    public static let onlyDate: DateFormatter = {
        let dateFormatter = DateFormatter()
        dateFormatter.timeStyle = .none
        dateFormatter.dateStyle = .full
        return dateFormatter
    }()
    
    /// Returns the time without the date.
    public static let onlyTime: DateFormatter = {
        let dateFormatter = DateFormatter()
        dateFormatter.timeStyle = .medium
        dateFormatter.dateStyle = .none
        return dateFormatter
    }()
}

//
//  ListItemModifier.swift
//  ${appName}
//

import SwiftUI

/*
 Task 3.3:
 Implement the Custom View Modifier Struct
*/

/// Custom modifier to surround listed dates with a rounded rectangle with blue stroke.
struct ListItemModifier: ViewModifier {
    func body(content: Content) -> some View {
        Text("")
    }
}
/// The corresponding view.
extension View {
    func listItemModifier() -> some View {
        ModifiedContent(content: self, modifier: ListItemModifier())
    }
}

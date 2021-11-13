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
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 25.0).stroke(Color.blue, lineWidth: 2.5))
            .padding(16)
    }
}
/// The corresponding view.
extension View {
    func listItemModifier() -> some View {
        ModifiedContent(content: self, modifier: ListItemModifier())
    }
}

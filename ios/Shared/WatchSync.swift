import Foundation

/// Keys for the WatchConnectivity messages between the iPhone app and the Apple Watch app.
enum WatchSync {
    /// JSON-encoded `WeeklyMenu`.
    static let menuKey = "menu"
    /// The iPhone's default cafeteria, used as the watch's first pick.
    static let defaultCafeteriaKey = "defaultCafeteria"
    static let sentAtKey = "sentAt"
    /// Message from the watch: `"menu"` = send what you have, `"refresh"` = reload from the site too.
    static let requestKey = "request"
}

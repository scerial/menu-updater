import Foundation
import WatchConnectivity

/// Sends the current menu to the paired Apple Watch and answers its requests.
final class PhoneConnectivity: NSObject, WCSessionDelegate, @unchecked Sendable {
    static let shared = PhoneConnectivity()

    /// Set by the view model: reload the menu from the site (the watch asked for a refresh).
    var onRefreshRequest: (() -> Void)?

    private let lock = NSLock()
    private var latest: Data?

    func start() {
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Remembers the menu and, when a watch with the app is paired, delivers it as the application context
    /// (the system keeps the newest one and hands it over even if the watch app is not running).
    func push(_ menu: WeeklyMenu) {
        guard let data = try? JSONEncoder().encode(menu) else { return }
        lock.lock(); latest = data; lock.unlock()
        send(data)
    }

    private func payload(_ data: Data) -> [String: Any] {
        [
            WatchSync.menuKey: data,
            WatchSync.defaultCafeteriaKey: UserDefaults.standard.string(forKey: "defaultCafeteria") ?? "",
            WatchSync.sentAtKey: Date().timeIntervalSince1970,
        ]
    }

    private func send(_ data: Data) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else { return }
        try? session.updateApplicationContext(payload(data))
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        lock.lock(); let data = latest; lock.unlock()
        if activationState == .activated, let data { send(data) }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    func sessionWatchStateDidChange(_ session: WCSession) {
        lock.lock(); let data = latest; lock.unlock()
        if let data { send(data) }
    }

    func session(_ session: WCSession, didReceiveMessage message: [String: Any], replyHandler: @escaping ([String: Any]) -> Void) {
        if message[WatchSync.requestKey] as? String == "refresh" {
            DispatchQueue.main.async { self.onRefreshRequest?() }
        }
        lock.lock(); let data = latest; lock.unlock()
        replyHandler(data.map { payload($0) } ?? [:])
    }
}

import SwiftUI
import WatchConnectivity

@main
struct WeeklyMenuWatchApp: App {
    @State private var store = WatchStore()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            WatchRootView(store: store)
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { store.requestMenu() }
        }
    }
}

/// Holds the menu the iPhone sent. The watch never loads menus itself; the iPhone app does the
/// download and OCR and hands the result over WatchConnectivity.
@MainActor
@Observable
final class WatchStore: NSObject, WCSessionDelegate {
    var menu: WeeklyMenu?
    var phoneDefaultCafeteria = ""
    var isRequesting = false
    var statusMessage: String?

    private let cacheKey = "watch-cached-menu"
    private let defaultKey = "watch-phone-default"

    override init() {
        super.init()
        let defaults = UserDefaults.standard
        if let data = defaults.data(forKey: cacheKey) { menu = try? JSONDecoder().decode(WeeklyMenu.self, from: data) }
        phoneDefaultCafeteria = defaults.string(forKey: defaultKey) ?? ""
        guard WCSession.isSupported() else { return }
        WCSession.default.delegate = self
        WCSession.default.activate()
    }

    /// Ask the iPhone for the menu; `refresh` makes it reload from the site as well.
    func requestMenu(refresh: Bool = false) {
        let session = WCSession.default
        guard session.activationState == .activated else { return }
        apply(session.receivedApplicationContext)
        guard session.isReachable else {
            if menu == nil { statusMessage = "iPhone을 찾을 수 없습니다" }
            return
        }
        isRequesting = true
        session.sendMessage([WatchSync.requestKey: refresh ? "refresh" : "menu"]) { [weak self] reply in
            let data = reply[WatchSync.menuKey] as? Data
            let phoneDefault = reply[WatchSync.defaultCafeteriaKey] as? String
            Task { @MainActor in
                self?.isRequesting = false
                self?.accept(data, phoneDefault: phoneDefault)
            }
        } errorHandler: { [weak self] _ in
            Task { @MainActor in
                self?.isRequesting = false
                if self?.menu == nil { self?.statusMessage = "iPhone과 연결하지 못했습니다" }
            }
        }
    }

    private func apply(_ context: [String: Any]) {
        accept(context[WatchSync.menuKey] as? Data, phoneDefault: context[WatchSync.defaultCafeteriaKey] as? String)
    }

    private func accept(_ data: Data?, phoneDefault: String?) {
        if let phoneDefault, !phoneDefault.isEmpty {
            phoneDefaultCafeteria = phoneDefault
            UserDefaults.standard.set(phoneDefault, forKey: defaultKey)
        }
        guard let data, let decoded = try? JSONDecoder().decode(WeeklyMenu.self, from: data) else { return }
        statusMessage = nil
        if decoded != menu { menu = decoded }
        UserDefaults.standard.set(data, forKey: cacheKey)
    }

    // MARK: WCSessionDelegate (called on a background queue)


    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        guard activationState == .activated else { return }
        Task { @MainActor in self.requestMenu() }
    }

    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        let data = applicationContext[WatchSync.menuKey] as? Data
        let phoneDefault = applicationContext[WatchSync.defaultCafeteriaKey] as? String
        Task { @MainActor in self.accept(data, phoneDefault: phoneDefault) }
    }

    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        guard session.isReachable else { return }
        Task { @MainActor in if self.menu == nil { self.requestMenu() } }
    }
}

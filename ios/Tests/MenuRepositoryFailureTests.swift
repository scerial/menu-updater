import XCTest
import UIKit

@testable import WeeklyMenu

/// Serves canned responses so repository failure paths can be exercised without the network.
private final class StubURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) throws -> (Int, Data))?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        do {
            let (status, data) = try Self.handler?(request) ?? (500, Data())
            let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

final class MenuRepositoryFailureTests: XCTestCase {
    private var repository: MenuRepository!

    override func setUp() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [StubURLProtocol.self]
        repository = MenuRepository(session: URLSession(configuration: configuration))
    }

    override func tearDown() {
        StubURLProtocol.handler = nil
    }

    private func html(_ string: String) -> (Int, Data) { (200, Data(string.utf8)) }

    private let boardWithPost = """
    <ul class="li_body"><li class="tit"><a href="/today/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></li><li class="time">2026-10-16</li></ul>
    """

    func testNetworkFailureSurfacesTheUnderlyingError() async {
        StubURLProtocol.handler = { _ in throw URLError(.notConnectedToInternet) }

        do {
            _ = try await repository.fetchLatestMenu()
            XCTFail("Expected an error")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .notConnectedToInternet)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testServerErrorIsReportedAsBadResponse() async {
        StubURLProtocol.handler = { _ in (503, Data()) }

        do {
            _ = try await repository.fetchLatestMenu()
            XCTFail("Expected an error")
        } catch let error as URLError {
            XCTAssertEqual(error.code, .badServerResponse)
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testBoardWithoutMatchingPostThrowsNoEligiblePost() async {
        StubURLProtocol.handler = { [self] _ in
            html(#"<ul class="li_body"><li class="tit"><a href="/today/?bmode=view&amp;idx=2">26년 10월 4주차 다른학교 식단</a></li><li>2026-10-16</li></ul>"#)
        }

        do {
            _ = try await repository.fetchLatestMenu()
            XCTFail("Expected an error")
        } catch let error as MenuError {
            guard case .noEligiblePost = error else { return XCTFail("Unexpected error: \(error)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testPostWithoutImagesThrowsNoMenuImages() async {
        StubURLProtocol.handler = { [self] request in
            // The detail page URL carries idx=1; the board URL does not.
            request.url?.absoluteString.contains("idx=1") == true ? html("<p>No images here</p>") : html(boardWithPost)
        }

        do {
            _ = try await repository.fetchLatestMenu()
            XCTFail("Expected an error")
        } catch let error as MenuError {
            guard case .noMenuImages = error else { return XCTFail("Unexpected error: \(error)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testUnreadableImageThrowsNoRecognizedText() async {
        StubURLProtocol.handler = { [self] request in
            let url = request.url?.absoluteString ?? ""
            if url.contains("cdn.imweb.me") { return (200, Data("not an image".utf8)) }
            if url.contains("idx=1") { return html(#"<img src="https://cdn.imweb.me/upload/a.png">"#) }
            return html(boardWithPost)
        }

        do {
            _ = try await repository.fetchLatestMenu()
            XCTFail("Expected an error")
        } catch let error as MenuError {
            guard case .noRecognizedText = error else { return XCTFail("Unexpected error: \(error)") }
        } catch {
            XCTFail("Unexpected error: \(error)")
        }
    }

    func testOneFailedImageDoesNotDiscardTheOtherCafeteria() async throws {
        let menuImage = Self.renderMenuImage()
        StubURLProtocol.handler = { [self] request in
            let url = request.url?.absoluteString ?? ""
            if url.contains("broken.png") { return (503, Data()) }
            if url.contains("good.png") { return (200, menuImage) }
            if url.contains("idx=1") {
                return html(#"<img src="https://cdn.imweb.me/upload/broken.png"><img src="https://cdn.imweb.me/upload/good.png">"#)
            }
            return html(boardWithPost)
        }

        let menu = try await repository.fetchLatestMenu()

        XCTAssertEqual(menu.tables.count, 1)
        XCTAssertEqual(menu.tables.first?.days.first?.day, 20)
    }

    /// A minimal one-column menu table: a date header with one item below it, read by the real Vision OCR.
    private static func renderMenuImage() -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 800, height: 500))
        return renderer.pngData { context in
            UIColor.white.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 800, height: 500))
            let attributes: [NSAttributedString.Key: Any] = [.font: UIFont.systemFont(ofSize: 48), .foregroundColor: UIColor.black]
            ("10월20일(화)" as NSString).draw(at: CGPoint(x: 250, y: 60), withAttributes: attributes)
            ("김치볶음밥" as NSString).draw(at: CGPoint(x: 270, y: 250), withAttributes: attributes)
        }
    }

    private let boardWithBothPosts = """
    <ul class="li_body"><li class="tit"><a href="/today/?bmode=view&amp;idx=2">26년 10월 4주차 순천향대학교 의과대학 식단</a></li><li class="time">2026-10-16</li></ul>
    <ul class="li_body"><li class="tit"><a href="/today/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></li><li class="time">2026-10-16</li></ul>
    """

    func testLoadsBothTheUniversityAndMedicalSchoolPosts() async throws {
        let menuImage = Self.renderMenuImage()
        StubURLProtocol.handler = { [self] request in
            let url = request.url?.absoluteString ?? ""
            if url.contains("cdn.imweb.me") { return (200, menuImage) }
            if url.contains("idx=1") { return html(#"<img src="https://cdn.imweb.me/upload/main.png">"#) }
            if url.contains("idx=2") { return html(#"<img src="https://cdn.imweb.me/upload/med.png">"#) }
            return html(boardWithBothPosts)
        }

        let menu = try await repository.fetchLatestMenu()

        XCTAssertEqual(menu.tables.count, 2)
        XCTAssertEqual(menu.title, "26년 10월 4주차 순천향대학교 식단")
        XCTAssertEqual(menu.additionalSources?.map(\.title), ["26년 10월 4주차 순천향대학교 의과대학 식단"])
        XCTAssertEqual(menu.sources.count, 2)
    }

    func testFailedMedicalPostKeepsTheUniversityMenu() async throws {
        let menuImage = Self.renderMenuImage()
        StubURLProtocol.handler = { [self] request in
            let url = request.url?.absoluteString ?? ""
            if url.contains("cdn.imweb.me") { return (200, menuImage) }
            if url.contains("idx=2") { return (503, Data()) }
            if url.contains("idx=1") { return html(#"<img src="https://cdn.imweb.me/upload/main.png">"#) }
            return html(boardWithBothPosts)
        }

        let menu = try await repository.fetchLatestMenu()

        XCTAssertEqual(menu.tables.count, 1)
        XCTAssertEqual(menu.title, "26년 10월 4주차 순천향대학교 식단")
        XCTAssertNil(menu.additionalSources?.first)
    }

    func testCachedMenuRoundTripsForOfflineDisplay() throws {
        let table = try XCTUnwrap(MenuTableParser.parse([
            RecognizedLine(text: "10월20일(화)", box: CGRect(x: 0.3, y: 0.8, width: 0.1, height: 0.02)),
            RecognizedLine(text: "밥", box: CGRect(x: 0.3, y: 0.7, width: 0.05, height: 0.02))
        ]))
        let menu = WeeklyMenu(
            title: "cached", postedAt: "2026-10-16",
            sourceURL: URL(string: "https://www.agoodplacefnb.com/today")!, tables: [table]
        )

        addTeardownBlock { UserDefaults.standard.removeObject(forKey: "cached-weekly-menu-v2") }
        repository.saveCachedMenu(menu)
        let loaded = try XCTUnwrap(repository.loadCachedMenu())

        XCTAssertEqual(loaded.title, "cached")
        XCTAssertEqual(loaded.tables, [table])
    }
}

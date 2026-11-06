import Foundation
import Vision
import ImageIO
import SwiftSoup

struct MenuPost {
    let title: String
    let date: String
    let url: URL
}

enum MenuError: LocalizedError {
    case noEligiblePost
    case noMenuImages
    case noRecognizedText

    var errorDescription: String? {
        switch self {
        case .noEligiblePost: return String(localized: "순천향대학교 메뉴 게시물을 찾지 못했습니다.")
        case .noMenuImages: return String(localized: "게시물에 메뉴 이미지가 없습니다.")
        case .noRecognizedText: return String(localized: "메뉴 이미지에서 글자를 읽지 못했습니다.")
        }
    }
}

struct MenuRepository {
    private let session: URLSession
    private let cacheKey = "cached-weekly-menu-v2"
    private let boardURL = URL(string: "https://www.agoodplacefnb.com/today")!

    init(session: URLSession = .shared) {
        self.session = session
    }

    /// Loads the latest 순천향대학교 post and the matching 의과대학 post, and merges their tables.
    /// One post failing doesn't discard the other; the load only fails when neither produces a menu.
    func fetchLatestMenu() async throws -> WeeklyMenu {
        let found = try await findLatestPosts()

        var tables: [MenuTable] = []
        var sources: [MenuSource] = []
        var firstError: Error?
        for post in [found.university, found.medical].compactMap({ $0 }) {
            do {
                tables += try await fetchTables(for: post)
                sources.append(MenuSource(title: post.title, postedAt: post.date, url: post.url))
            } catch {
                firstError = firstError ?? error
            }
        }
        guard let primary = sources.first else { throw firstError ?? MenuError.noRecognizedText }

        return WeeklyMenu(
            title: primary.title, postedAt: primary.postedAt, sourceURL: primary.url,
            tables: tables, additionalSources: Array(sources.dropFirst())
        )
    }

    private func fetchTables(for post: MenuPost) async throws -> [MenuTable] {
        let html = try await fetchHTML(post.url)
        let imageURLs = try Self.extractMenuImageURLs(from: html, baseURL: post.url)
        guard !imageURLs.isEmpty else { throw MenuError.noMenuImages }

        // Each image is a separate cafeteria's table. One image failing to download or parse
        // shouldn't discard the others; only fail when no image produced a table.
        var tables: [MenuTable] = []
        var firstError: Error?
        for imageURL in imageURLs {
            do {
                let data = try await fetchData(imageURL)
                let lines = try await recognizeText(in: data)
                if let table = MenuTableParser.parse(lines) {
                    tables.append(table)
                }
            } catch {
                firstError = firstError ?? error
            }
        }
        guard !tables.isEmpty else { throw firstError ?? MenuError.noRecognizedText }
        return tables
    }

    func loadCachedMenu() -> WeeklyMenu? {
        guard let data = UserDefaults.standard.data(forKey: cacheKey) else { return nil }
        return try? JSONDecoder().decode(WeeklyMenu.self, from: data)
    }

    func saveCachedMenu(_ menu: WeeklyMenu) {
        guard let data = try? JSONEncoder().encode(menu) else { return }
        UserDefaults.standard.set(data, forKey: cacheKey)
    }

    private func findLatestPosts() async throws -> (university: MenuPost?, medical: MenuPost?) {
        var universityPosts: [MenuPost] = []
        var medicalPosts: [MenuPost] = []
        var firstError: Error?
        for page in 1...5 {
            var components = URLComponents(url: boardURL, resolvingAgainstBaseURL: false)!
            if page > 1 {
                components.queryItems = [
                    URLQueryItem(name: "q", value: "YToxOntzOjEyOiJrZXl3b3JkX3R5cGUiO3M6MzoiYWxsIjt9"),
                    URLQueryItem(name: "page", value: String(page))
                ]
            }
            // A single failing page shouldn't discard posts found on other pages.
            do {
                let html = try await fetchHTML(components.url!)
                universityPosts.append(contentsOf: try Self.eligiblePosts(in: html, baseURL: boardURL))
                medicalPosts.append(contentsOf: try Self.medicalSchoolPosts(in: html, baseURL: boardURL))
            } catch {
                firstError = firstError ?? error
            }
            // The board lists newest posts first, and both posts of a week go up together,
            // so the first page with a match holds the latest of each.
            if !universityPosts.isEmpty || !medicalPosts.isEmpty { break }
        }
        let university = universityPosts.max { $0.date < $1.date }
        let medical = medicalPosts.max { $0.date < $1.date }
        guard university != nil || medical != nil else {
            throw firstError ?? MenuError.noEligiblePost
        }
        return (university, medical)
    }

    private func fetchData(_ url: URL) async throws -> Data {
        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue("Mozilla/5.0 (compatible; SCHMenu; +https://scerial.github.io/menu-updater/privacy/)", forHTTPHeaderField: "User-Agent")
        let (data, response) = try await session.data(for: request)
        guard let response = response as? HTTPURLResponse,
              (200..<300).contains(response.statusCode) else {
            throw URLError(.badServerResponse)
        }
        return data
    }

    private func fetchHTML(_ url: URL) async throws -> String {
        let data = try await fetchData(url)
        guard let html = String(data: data, encoding: .utf8) else {
            throw URLError(.cannotDecodeContentData)
        }
        return html
    }

    private func recognizeText(in imageData: Data) async throws -> [RecognizedLine] {
        guard let source = CGImageSourceCreateWithData(imageData as CFData, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
            throw MenuError.noRecognizedText
        }
        // Vision's synchronous `perform` blocks, so keep it off the caller's executor.
        return try await Task.detached {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["ko-KR", "en-US"]
            request.usesLanguageCorrection = true
            try VNImageRequestHandler(cgImage: image).perform([request])
            return (request.results ?? []).compactMap { observation in
                observation.topCandidates(1).first.map { RecognizedLine(text: $0.string, box: observation.boundingBox) }
            }
        }.value
    }

    static func findEligiblePost(in html: String, baseURL: URL) throws -> MenuPost? {
        try eligiblePosts(in: html, baseURL: baseURL).first
    }

    /// Posts for the main campus cafeterias (순천향대학교, not 의과대학).
    static func eligiblePosts(in html: String, baseURL: URL) throws -> [MenuPost] {
        try posts(in: html, baseURL: baseURL) { $0.contains("순천향대학교") && !$0.contains("의과대학") }
    }

    /// Posts for the 의과대학 (천안의대) cafeteria.
    static func medicalSchoolPosts(in html: String, baseURL: URL) throws -> [MenuPost] {
        try posts(in: html, baseURL: baseURL) { $0.contains("순천향대학교") && $0.contains("의과대학") }
    }

    private static func posts(in html: String, baseURL: URL, where isEligible: (String) -> Bool) throws -> [MenuPost] {
        let document = try SwiftSoup.parse(html)
        let links = try document.select("a[href*='bmode=view']")
        var posts: [MenuPost] = []
        for link in links.array() {
            let title = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard isEligible(title) else { continue }
            guard let url = URL(string: try link.attr("href"), relativeTo: baseURL)?.absoluteURL else { continue }
            let row = link.parent()?.parent()
            // Recent posts show a relative time ("3시간전"); the full date is only in `li.time`'s title.
            let timeTitle = try row?.select("li.time").first()?.attr("title") ?? ""
            let rowText = try row?.text() ?? ""
            // Posts without a parseable date can't be ordered, so they are skipped.
            guard let date = [timeTitle, rowText].lazy.compactMap({ text in
                text.range(of: #"\d{4}-\d{2}-\d{2}"#, options: .regularExpression).map { String(text[$0]) }
            }).first else { continue }
            posts.append(MenuPost(title: title, date: date, url: url))
        }
        return posts
    }

    static func extractMenuImageURLs(from html: String, baseURL: URL) throws -> [URL] {
        let document = try SwiftSoup.parse(html)
        return try document.select("img[src]").array().compactMap { image in
            let source = try image.attr("src")
            guard source.contains("cdn.imweb.me/upload") else { return nil }
            return URL(string: source, relativeTo: baseURL)?.absoluteURL
        }
    }
}
import XCTest
@testable import WeeklyMenu

final class MenuParsingTests: XCTestCase {
    func testFindsEligibleUniversityPostAndExcludesMedicalSchool() throws {
        let html = """
        <table>
          <tr><td><a href="/?bmode=view&amp;idx=2">26년 10월 4주차 순천향대학교 의과대학 식단</a></td><td>2026-10-16</td></tr>
          <tr><td><a href="/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></td><td>2026-10-16</td></tr>
        </table>
        """

        let post = try MenuRepository.findEligiblePost(
            in: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertEqual(post?.title, "26년 10월 4주차 순천향대학교 식단")
        XCTAssertEqual(post?.url.absoluteString, "https://www.agoodplacefnb.com/?bmode=view&idx=1")
    }

    func testEligiblePostsIncludesOnlyMatchingTitles() throws {
        let html = """
        <table>
          <tr><td><a href="/?bmode=view&amp;idx=3">26년 10월 4주차 순천향대학교 의과대학 식단</a></td><td>2026-10-16</td></tr>
          <tr><td><a href="/?bmode=view&amp;idx=2">26년 10월 3주차 순천향대학교 식단</a></td><td>2026-10-08</td></tr>
          <tr><td><a href="/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></td><td>2026-10-16</td></tr>
        </table>
        """

        let posts = try MenuRepository.eligiblePosts(
            in: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertEqual(posts.count, 2)
        XCTAssertFalse(posts.contains { $0.title.contains("의과대학") })
    }

    func testMedicalSchoolPostsAreFoundSeparately() throws {
        let html = """
        <table>
          <tr><td><a href="/?bmode=view&amp;idx=2">26년 10월 4주차 순천향대학교 의과대학 식단</a></td><td>2026-10-16</td></tr>
          <tr><td><a href="/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></td><td>2026-10-16</td></tr>
        </table>
        """

        let posts = try MenuRepository.medicalSchoolPosts(
            in: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertEqual(posts.map(\.title), ["26년 10월 4주차 순천향대학교 의과대학 식단"])
    }

    func testExtractsAllUploadedMenuImages() throws {
        let html = """
        <img src="https://cdn.imweb.me/upload/menu-a.png">
        <img src="https://cdn.imweb.me/upload/menu-b.png">
        <img src="https://cdn.imweb.me/thumbnail/logo.png">
        """

        let images = try MenuRepository.extractMenuImageURLs(
            from: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertEqual(images.count, 2)
    }

    func testPostsWithoutDateAreSkipped() throws {
        let html = """
        <table>
          <tr><td><a href="/?bmode=view&amp;idx=1">26년 10월 4주차 순천향대학교 식단</a></td><td>no date here</td></tr>
        </table>
        """

        let posts = try MenuRepository.eligiblePosts(
            in: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertTrue(posts.isEmpty)
    }
}
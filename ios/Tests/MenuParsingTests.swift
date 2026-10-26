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

    func testRecentPostWithRelativeTimeUsesDateFromTitleAttribute() throws {
        let html = """
        <ul class="li_body"><li class="tit"><a class="list_text_title" href="/today/?bmode=view&amp;idx=2"><span>26년 10월 5주차 순천향대학교 식단</span></a></li><li class="time" title="2026-10-26 09:46">3시간전</li></ul>
        <ul class="li_body"><li class="tit"><a class="list_text_title" href="/today/?bmode=view&amp;idx=1"><span>26년 10월 4주차 순천향대학교 식단</span></a></li><li class="time" title="2026-10-16 09:45">2026-10-16</li></ul>
        """

        let posts = try MenuRepository.eligiblePosts(
            in: html,
            baseURL: URL(string: "https://www.agoodplacefnb.com/today")!
        )

        XCTAssertEqual(posts.map(\.date), ["2026-10-26", "2026-10-16"])
        XCTAssertEqual(posts.max { $0.date < $1.date }?.title, "26년 10월 5주차 순천향대학교 식단")
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
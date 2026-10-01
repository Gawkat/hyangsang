# Feed Backlog

Candidate RSS feeds to add alongside the built-in Yonhap, BBC Korean and SBS feeds. Everything
below was fetched on 2026-09-27: the feed had to parse as RSS 2.0 and be updated recently, and a
sample article was run through `ArticleParser` to see how much Korean text came out.

Tiers are based on how much parser work each feed needs:

* **Tier 1**: `GenericContentsParser` already extracts the article body
* **Tier 2**: needs one shared parser for the ND Soft CMS (`#article-view-content-div`), which covers
  every site with `/rss/allArticle.xml` style feeds
* **Tier 3**: needs its own parser

"Date" notes the item date format where it differs from standard RSS. All of them except a missing
date are handled now (see [App gaps](#app-gaps-found-while-checking)).

## Picks for learners

Sources that add a register or topic the current feeds don't cover:

* **어린이동아**: children's newspaper, short sentences and simple vocabulary; the easiest reading
  of any live feed found (Tier 2)
* **한겨레 미래&과학 / 동아일보 과학·문학/출판·여행·건강**: non-news vocabulary with longer feature
  articles
* **VOA 한국어 세계 / 한반도, RFA 자유아시아방송**: broadcast scripts (`진행자)`, `앵커:`), so a
  spoken register
* **찾기쉬운 생활법령정보**: government plain-language explanations of everyday law (Tier 3)
* **한겨레21, 주간경향, 시사저널, 미디어오늘**: weekly magazine long reads
* **사설/칼럼 feeds** (한겨레, 경향, 동아): hardest register, good for advanced users

## Tier 1: works with the generic parser

| Source | Category | URL | Notes |
|---|---|---|---|
| 한겨레 | News | https://www.hani.co.kr/rss/ | Date: no item dates at all |
| 한겨레 | Politics | https://www.hani.co.kr/rss/politics/ | Same for all 한겨레 sections |
| 한겨레 | Society | https://www.hani.co.kr/rss/society/ | |
| 한겨레 | Economy | https://www.hani.co.kr/rss/economy/ | |
| 한겨레 | International | https://www.hani.co.kr/rss/international/ | |
| 한겨레 | Culture | https://www.hani.co.kr/rss/culture/ | |
| 한겨레 | Opinion | https://www.hani.co.kr/rss/opinion/ | 사설·칼럼 |
| 한겨레 | Science (new) | https://www.hani.co.kr/rss/science/ | 미래&과학, long articles |
| 한겨레 | Sports | https://www.hani.co.kr/rss/sports/ | |
| 한겨레21 | Magazine (new) | https://h21.hani.co.kr/rss/ | Date: none |
| 경향신문 | Politics | https://www.khan.co.kr/rss/rssdata/politic_news.xml | Date: `dc:date` only. Text-to-speech widget text ("기사 읽기 요약 기사를 재생 중이에요") leaks into the first block |
| 경향신문 | Culture | https://www.khan.co.kr/rss/rssdata/culture_news.xml | Same as above for all 경향 feeds |
| 경향신문 | Opinion | https://www.khan.co.kr/rss/rssdata/opinion_news.xml | |
| 경향신문 | Life (new) | https://www.khan.co.kr/rss/rssdata/life_news.xml | |
| 경향신문 | Society | https://www.khan.co.kr/rss/rssdata/society_news.xml | Extraction was partial on the sample |
| 경향신문 | Science (new) | https://www.khan.co.kr/rss/rssdata/science_news.xml | Extraction failed on the sample; treat as Tier 3 |
| 주간경향 | Magazine (new) | https://weekly.khan.co.kr/rss/rssdata/total_news.xml | Date: `dc:date` only |
| 세계일보 | News | https://www.segye.com/Articles/RSSList/segye_recent.xml | Date: `Sun,27 Sep` (no space) |
| 서울경제 | Economy | https://www.sedaily.com/rss/newsall | Section feeds: `/rss/economy`, `/rss/finance`, … |
| TV조선 | News | https://news.tvchosun.com/site/data/rss/rss.xml | Body arrives as one `<br>`-separated block, so no paragraph breaks |
| RFA 자유아시아방송 | North Korea | https://www.rfa.org/korean/rss2.xml | Live, but RFA's funding has been unstable since 2025 |
| VOA 한국어 세계 | International | https://www.voakorea.com/api/zpokyl-vomx-tpe_kjt | Most other VOA Korean feeds stopped in March 2025 |
| VOA 한국어 한반도 | North Korea | https://www.voakorea.com/api/zoikol-vomx-tpepgjp | |
| Daily NK | North Korea | https://www.dailynk.com/feed/ | |
| IT동아 | Tech (new) | https://it.donga.com/feeds/rss/ | Accessible consumer-tech writing |
| 지디넷코리아 | Tech (new) | https://feeds.feedburner.com/zdkorea | |
| 바이라인네트워크 | Tech (new) | https://byline.network/feed/ | |
| 전북일보 | Local | https://www.jjan.kr/news/rssAll | |

## Tier 2: one ND Soft CMS parser

All of these put the body in `#article-view-content-div`. The generic parser returns nothing or
navigation text for most of them. They also share the date format `2026-09-27 19:00:00` (no zone).

| Source | Category | URL | Notes |
|---|---|---|---|
| 어린이동아 | Kids (new) | https://cdn.kids.donga.com/rss/gns_allArticle.xml | Top learner pick |
| 미디어오늘 | Society | https://www.mediatoday.co.kr/rss/allArticle.xml | |
| 시사저널 | Magazine (new) | https://www.sisajournal.com/rss/allArticle.xml | |
| 블로터 | Tech (new) | https://www.bloter.net/rss/allArticle.xml | |
| 교수신문 | Education (new) | https://www.kyosu.net/rss/allArticle.xml | Academic register |
| 경남도민일보 | Local | https://www.idomin.com/rss/allArticle.xml | |
| 제주일보 | Local | https://www.jejunews.com/rss/allArticle.xml | Generic parser already works partly |

Unverified, but on the same CMS according to [newswatcher's list](https://github.com/seokhoonj/newswatcher/blob/main/docs/korean-news-rss.md):
강원도민일보 `kado.net`, 대전일보 `daejonilbo.com`, 인천일보 `incheonilbo.com`, 충청투데이
`cctoday.co.kr`, 테크M `techm.kr`, 그린포스트코리아 `greenpostkorea.co.kr`, 매일노동뉴스
`labortoday.co.kr` (all `/rss/allArticle.xml`).

## Tier 3: needs its own parser

| Source | Category | URL | Body location / notes |
|---|---|---|---|
| 동아일보 | News | https://rss.donga.com/total.xml | `.news_view`, `<br>`-separated text. Also has `politics`, `national`, `economy`, `international`, `culture`, `sports`, `editorials`, `science`, `book`, `travel`, `health`, `leisure`, `lifeinfo`, `inmul` feeds at `rss.donga.com/<section>.xml` |
| 조선일보 | News | https://www.chosun.com/arc/outboundfeeds/rss/?outputType=xml | Arc/Fusion: body is JSON in `Fusion.globalContent` (`content_elements`), not in the HTML |
| 조선비즈 | Economy | https://biz.chosun.com/arc/outboundfeeds/rss/?outputType=xml | Same Arc parser as 조선일보 |
| 노컷뉴스 | News | https://rss.nocutnews.co.kr/news/news.xml | `#pnlContent`, `<br>`-separated. Date: `27 09 2026` (numeric month). Sections: `/category/<politics\|economy\|society\|world\|culture>.xml` |
| 이데일리 | Economy | http://rss.edaily.co.kr/edaily_news.xml | `.news_body` |
| 뉴시스 | News | https://www.newsis.com/RSS/sokbo.xml | `<br>`-heavy; selector still to be found |
| 오마이뉴스 | News | http://rss.ohmynews.com/rss/ohmynews.xml | `.at_contents`; citizen journalism, more casual register |
| 국민일보 | News | https://www.kmib.co.kr/rss/data/kmibRssAll.xml | `#articleBody`. Date: `27 Sep  2026` (double space) |
| 한국경제 | Economy | https://www.hankyung.com/feed/all-news | `#articletxt` |
| 매일경제 | Economy | https://www.mk.co.kr/rss/30000001/ | Generic parser only got part of the body. Date: `+09:00` offset in RFC 1123 |
| 서울신문 | News | https://www.seoul.co.kr/xml/rss/google_plan.xml | Generic parser picks up the font-size menu |
| 코메디닷컴 | Health | https://kormedi.com/feed/ | WordPress `.entry-content`; the generic parser stops at an empty `<article>` first |
| 찾기쉬운 생활법령정보 | Law (new) | https://www.easylaw.go.kr/CSP/RssNewRetrieve.laf?topMenu=serviceUl7 | Plain-language government explainers; the generic parser picks up navigation. Date: `KST` zone name |
| MBC | News | https://imnews.imbc.com/rss/google_news/narrativeNews.rss | Body is barely in the static HTML; check whether a JSON endpoint exists |

## Checked and rejected

* **No working public feed**: KBS, YTN, 중앙일보, 한국일보, 문화일보, 뉴스1 (403), 채널A, NHK
  WORLD 한국어, 정책브리핑 korea.kr (documented `/rss/policy.xml` returns 404), 동아사이언스
* **Stale**: 어린이조선일보 (last item 2020), 시사IN (2024-06, article pages time out), JTBC
  `fs.jtbc.co.kr` newsflash (2024-10), most VOA 한국어 program feeds (March 2025)
* **Other problems**: JTBC's new `news-ex.jtbc.co.kr` section feeds (article pages are
  client-rendered), OSEN (article links 404), 연합뉴스TV (`/browse/feed/` is gone and `/add/rss` is
  an HTML page)
* **Possible user-added content**: Brunch author feeds (`https://brunch.co.kr/rss/@@<id>`) work and
  are good essay-style reading, but there's no topic feed, so they only fit a "let user add own
  content" flow

## App gaps found while checking

* Fixed: `RssFeedParser` now falls back to `dc:date` (경향신문, 주간경향), and `parseToIso8601`
  handles every date variant above, reading dates without a zone as Korean time. 한겨레 and 한겨레21
  still have no item dates at all, so those articles sort by `addedDate`
* Several sites (동아일보, 노컷뉴스, 뉴시스, TV조선) separate paragraphs with `<br>` inside a single
  container rather than using `<p>`, so splitting paragraphs on `<br><br>` would help more than one
  parser

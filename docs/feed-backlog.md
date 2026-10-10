# Feed Backlog

Candidate RSS feeds to add alongside the built-in Yonhap, BBC Korean, SBS, 한겨레, 동아일보, 어린이동아,
소년한국일보, 어린이 경제신문, 찾기쉬운 생활법령정보 and 슬로우뉴스 feeds. Everything below was fetched on 2026-09-27: the feed had to parse as RSS 2.0 and be updated
recently, and a sample article was run through `ArticleParser` to see how much Korean text came out.

Tiers are based on how much parser work each feed needs:

* **Tier 1**: `GenericContentsParser` already extracts the article body
* **Tier 2**: on the ND Soft CMS (`#article-view-content-div`), which covers every site with
  `/rss/allArticle.xml` style feeds. `NdSoftNewsParser` handles them, picked by `ArticleParser` from
  the page itself, so these only need adding to the default feeds
* **Tier 3**: needs its own parser

"Date" notes the item date format where it differs from standard RSS. All of them except a missing
date are handled now (see [App gaps](#app-gaps-found-while-checking)).

## Picks for learners

Sources that add a register or topic the current feeds don't cover:

* **VOA 한국어 한반도, RFA 자유아시아방송**: broadcast scripts (`진행자)`, `앵커:`), so a
  spoken register
* **한겨레21, 주간경향, 시사저널, 미디어오늘**: weekly magazine long reads
* **경향 사설/칼럼**: hardest register, good for advanced users

## Tier 1: works with the generic parser

| Source | Category | URL | Notes |
|---|---|---|---|
| 한겨레21 | Magazine (new) | https://h21.hani.co.kr/rss/ | Date: none. Article pages differ from www.hani.co.kr, so `HaniNewsParser` doesn't cover them |
| 경향신문 | Politics | https://www.khan.co.kr/rss/rssdata/politic_news.xml | Date: `dc:date` only. Text-to-speech widget text ("기사 읽기 요약 기사를 재생 중이에요") leaks into the first block |
| 경향신문 | Culture | https://www.khan.co.kr/rss/rssdata/culture_news.xml | Same as above for all 경향 feeds |
| 경향신문 | Opinion | https://www.khan.co.kr/rss/rssdata/opinion_news.xml | |
| 경향신문 | Life (new) | https://www.khan.co.kr/rss/rssdata/life_news.xml | |
| 경향신문 | Society | https://www.khan.co.kr/rss/rssdata/society_news.xml | Extraction was partial on the sample |
| 경향신문 | Science | https://www.khan.co.kr/rss/rssdata/science_news.xml | Extraction failed on the sample; treat as Tier 3 |
| 주간경향 | Magazine (new) | https://weekly.khan.co.kr/rss/rssdata/total_news.xml | Date: `dc:date` only |
| 세계일보 | News | https://www.segye.com/Articles/RSSList/segye_recent.xml | Date: `Sun,27 Sep` (no space) |
| 서울경제 | Economy | https://www.sedaily.com/rss/newsall | Section feeds: `/rss/economy`, `/rss/finance`, … |
| TV조선 | News | https://news.tvchosun.com/site/data/rss/rss.xml | Body arrives as one `<br>`-separated block, so no paragraph breaks |
| RFA 자유아시아방송 | North Korea | https://www.rfa.org/korean/rss2.xml | Live, but RFA's funding has been unstable since 2025 |
| Daily NK | North Korea | https://www.dailynk.com/feed/ | |
| IT동아 | Tech (new) | https://it.donga.com/feeds/rss/ | Accessible consumer-tech writing |
| 지디넷코리아 | Tech (new) | https://feeds.feedburner.com/zdkorea | |
| 바이라인네트워크 | Tech (new) | https://byline.network/feed/ | |
| 전북일보 | Local | https://www.jjan.kr/news/rssAll | |

## Tier 2: ND Soft CMS

All of these put the body in `#article-view-content-div`, which `NdSoftNewsParser` reads. The
generic parser returned nothing or navigation text for most of them. They also share the date format
`2026-09-27 19:00:00` (no zone). Each one below had a sample article checked against
`NdSoftNewsParser` on 2026-10-10.

| Source | Category | URL | Notes |
|---|---|---|---|
| 미디어오늘 | Society | https://www.mediatoday.co.kr/rss/allArticle.xml | |
| 시사저널 | Magazine (new) | https://www.sisajournal.com/rss/allArticle.xml | |
| 블로터 | Tech (new) | https://www.bloter.net/rss/allArticle.xml | |
| 교수신문 | Education (new) | https://www.kyosu.net/rss/allArticle.xml | Academic register |
| 경남도민일보 | Local | https://www.idomin.com/rss/allArticle.xml | |
| 제주일보 | Local | https://www.jejunews.com/rss/allArticle.xml | Older skin: share buttons and reporter box sit inside the body |

Found on 2026-10-10 and on the same CMS, but not yet checked against `NdSoftNewsParser`:

| Source | Category | URL | Notes |
|---|---|---|---|
| 헬로디디 | Science | https://www.hellodd.com/rss/allArticle.xml | Science and research news from Daedeok |

Unverified, but on the same CMS according to [newswatcher's list](https://github.com/seokhoonj/newswatcher/blob/main/docs/korean-news-rss.md):
강원도민일보 `kado.net`, 대전일보 `daejonilbo.com`, 인천일보 `incheonilbo.com`, 충청투데이
`cctoday.co.kr`, 테크M `techm.kr`, 그린포스트코리아 `greenpostkorea.co.kr`, 매일노동뉴스
`labortoday.co.kr` (all `/rss/allArticle.xml`).

## Tier 3: needs its own parser

| Source | Category | URL | Body location / notes |
|---|---|---|---|
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
| MBC | News | https://imnews.imbc.com/rss/google_news/narrativeNews.rss | Body is barely in the static HTML; check whether a JSON endpoint exists |
| VOA 한국어 한반도 | North Korea | https://www.voakorea.com/api/zoikol-vomx-tpepgjp | See [VOA 한국어](#voa-한국어) |
| VOA 한국어 세계 | International | https://www.voakorea.com/api/zpokyl-vomx-tpe_kjt | See [VOA 한국어](#voa-한국어) |

### VOA 한국어

Rechecked on 2026-10-10. Of the 16 feeds on `voakorea.com/rssfeeds`, only 한반도 and 세계 are
still updated daily. The program feeds stopped in March 2025, apart from a stray item since in
우크라이나, 여기까지 왔습니다 and VOA 매일영어 플러스 (which teaches English anyway).

The generic parser finds the body but returns about 60 other blocks with it: share buttons, the
print menu, logos and related news. The body is in `.wsw` on every page checked. A VOA parser
also has to handle:

* Video and podcast pages with no text, about 6 of the 20 items in 한반도. Some stories appear
  twice, once as a video and once as an article
* An item at the top of each feed that links to the section page (`/z/2712`, `/z/2698`)

한반도 is the one to add. It covers North Korea in depth, and its program segments
(`[한국은 지금]`, `[뉴스 동서남북]`) are long 진행자)/기자) dialogues. 세계 is wire-style
international news, which Yonhap, SBS and BBC Korean already cover.

VOA is a US government broadcaster and has been under political pressure since 2025, which shows
in some headlines. That argues for adding 한반도 turned off, or with the source made clear.

## Checked and rejected

* **No working public feed**: KBS, YTN, 중앙일보, 한국일보, 문화일보, 뉴스1 (403), 채널A, NHK
  WORLD 한국어, 정책브리핑 korea.kr (documented `/rss/policy.xml` returns 404), 동아사이언스
* **Duplicates**: 동아일보 도서 `rss.donga.com/book.xml`, as its articles are all in the 문화 feed
  too. Articles are stored once per URL, so it ended up with almost none of its own
* **Stale**: 어린이조선일보 (last item 2020), 동아일보 레포츠 `rss.donga.com/leisure.xml` (2018),
  시사IN (2024-06, article pages time out), JTBC `fs.jtbc.co.kr` newsflash (2024-10), VOA 한국어
  program feeds (March 2025, see [VOA 한국어](#voa-한국어))
* **No feed found (2026-10-10)**: 사이언스타임즈, ㅍㅍㅅㅅ, 대학내일, 뉴닉, 문장웹진, KBS WORLD 한국어,
  텐아시아, 엑스포츠뉴스, 마이데일리, 대한민국 구석구석, 국립국어원, 국가유산청, EBS, 위키백과 (the
  featured article feeds aren't set up for Korean)
* **Other problems**: JTBC's new `news-ex.jtbc.co.kr` section feeds (article pages are
  client-rendered), OSEN (article links 404), 연합뉴스TV (`/browse/feed/` is gone and `/add/rss` is
  an HTML page)
* **Possible user-added content**: Brunch author feeds (`https://brunch.co.kr/rss/@@<id>`) work and
  are good essay-style reading, but there's no topic feed, so they only fit a "let user add own
  content" flow

## Coverage gaps

Reviewed on 2026-10-10 against the built-in feeds, leaving parser work aside.

* **Difficulty levels**: the children's papers (어린이동아, 소년한국일보, 어린이 경제신문) are the
  only easy sources, and after them everything jumps to newspaper level, apart from
  슬로우뉴스's explainers. No RSS feed of easy Korean news for adult learners turned up, only
  apps.
* **Fiction and poetry**: only 소년한국일보's 동시·동화, the only feed found. Literary
  webzines such as 문장웹진 have no feed, and Brunch only has per-author feeds.
* **Spoken register**: only VOA 한반도 and RFA, both about North Korea. No feed of everyday
  conversation or interviews was found.
* **Political balance**: 동아일보 is the conservative counterpart to the progressive 한겨레.
* **Lifestyle and pop culture**: food, travel and hobbies only come from 동아's travel and
  생활정보 sections. Pop culture only comes from the Yonhap and SBS entertainment feeds, since the
  dedicated outlets checked have no feed or a broken one.
* **Science**: 한겨레 and 동아 science are built in. 헬로디디 and 경향 science are the candidates,
  as 동아사이언스 and 사이언스타임즈 have no feed.

Other things to weigh when picking defaults:

* **Duplicates**: local papers often carry Yonhap wire stories, so adding many local or general
  news feeds mostly adds copies of stories that are already there. They're different URLs, so
  they aren't skipped as duplicates.
* **Volume**: Yonhap has 14 of the built-in feeds, so it dominates Discover. More general news
  makes that worse, while a low-volume weekly like 어린이 경제신문 gets buried.
* **Government broadcasters**: VOA and RFA are US government funded, and Yonhap is Korean state
  funded. That's fine for reading, but the source should be clear to users.
* **New categories**: Magazine, Tech and Education would each need a `DefaultCategory`
  with strings in both languages, so it's worth adding them only for more than one feed.

## App gaps found while checking

* Fixed: `RssFeedParser` now falls back to `dc:date` (경향신문, 주간경향), and `parseToIso8601`
  handles every date variant above, reading dates without a zone as Korean time. 한겨레 and 한겨레21
  still have no item dates at all, so those articles sort by `addedDate`
* 소년한국일보's daily 초등 속담 팩트체크 and 초등한자 따라 쓰기, and its comics, are images with no
  text, so they show up as articles with nothing to look up
* Several sites (동아일보, 노컷뉴스, 뉴시스, TV조선) separate paragraphs with `<br>` inside a single
  container rather than using `<p>`. `DongaNewsParser` splits them on `<br><br>`, which could be
  shared with the parsers for the others
* 찾기쉬운 생활법령정보 links every 솔로몬의 재판 case to `SolomonRetrieveLst.laf`, the case currently
  being voted on, so the cases are left out: they'd share one URL, and later cases would be skipped
  as duplicates. Each case has its own page, `SolomonRetrieve.laf?trialNo=<n>`, but the trial
  number isn't in the feed, so the sync would have to fetch the current case's page to find it

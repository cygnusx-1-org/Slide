# Markdown Renderer Examples

Real Reddit examples for validating Slide's two markdown renderers — old/snudown
(`body_html`) vs new-Reddit/Markwon (raw `body`). See issue #179.

Re-fetch raw `body` vs `body_html` with:
`scripts/reddit_fetch_comments.py <permalink>`
(userless OAuth — RedReader client id `yH0aTnJEt6qUgGn835B4vg`,
UA `org.quantumbadger.redreader/1.25.1`).

## Spoilers — native `>!…!<`
- https://old.reddit.com/r/help/comments/12hoj94/how_do_you_mark_a_comment_as_a_spoiler/
  — `>!secret text!<` in prose AND a literal `>!secret text!<` inside an indented
  code block. Edge case: spoiler must NOT trigger inside code.

## Spoilers — legacy link-style `[x](/s)` `(#s)` `(/spoiler)` `(#spoiler)`
- https://old.reddit.com/r/csshelp/comments/582cr7/how_to_add_a_spoiler_tag_to_css/
  — Q&A documenting the legacy syntaxes.
- https://old.reddit.com/r/csshelp/wiki/moresnippets#wiki_36._spoiler_tags
  — Canonical CSS: `a[href$="/spoiler"], a[href$="#spoiler"], a[href$="/s"], a[href$="#s"]`
  (link counts as a spoiler when its href ends with these). Negative case from the
  thread: `[trial](#does it work)` is a plain link, NOT a spoiler.

## Spoilers — legacy title-attribute `[teaser](/s "hidden text")` (r/anime convention)
- https://old.reddit.com/r/anime/comments/jhqdgv/_/ga4uegz/
  — `[teaser](/s "the culture festival confession")` — hidden text in the link title.
- https://old.reddit.com/r/anime/comments/omq77f/ , https://old.reddit.com/r/anime/comments/lyzf3s/
  — many more title-attr and link-style spoilers.

## Blockquotes
- https://old.reddit.com/r/space/comments/1ucowfv/_/ot5g32l/
  — Multi-paragraph quote (8+ blank-line-separated `>` paragraphs). snudown = one
  continuous stripe; Markwon segments unless `BlockquoteNormalizer` bridges them.
- https://old.reddit.com/r/todayilearned/comments/1uct2u6/_/ot6hpz4/
  — Single blockquote followed by plain commentary (quote-then-reply).

## Links — bare-URL autolink
- https://old.reddit.com/r/hackernews/comments/1udjsd1/_/otc7oni
  — `Discussion on HN: https://news.ycombinator.com/item?id=48645173` — bare URL must
  autolink (LinkifyPlugin; cf. RedditMarkwonLinkTest).

## Tables (color/style validation)
- https://old.reddit.com/r/buildapc/comments/1ucydmb/_/otbpndt/
  — PCPartPicker table: `:----` alignment, bold cells, in-cell links.

## Horizontal rule (color/style validation)
- https://old.reddit.com/r/dataisbeautiful/comments/1ucnzly/_/ota6ytf/
  — AutoMod comment with a `---` divider.

## Zero-width space `&#x200B;`
- https://old.reddit.com/comments/1ubxiza (r/BambuLab)
  — OP selftext uses `&#x200B;` as a blank-line spacer paragraph
  (`…everyone.\n\n&#x200B;\n\nSo I'm…`). Slide fetches selftext WITHOUT
  `raw_json=1`, so it arrives double-escaped as `&amp;#x200B;` — the form
  `RedditMarkwon.ZERO_WIDTH_SPACE_ENTITY` strips to U+200B.

## Code blocks (fenced ```)
- (User has saved example — add URL here.)

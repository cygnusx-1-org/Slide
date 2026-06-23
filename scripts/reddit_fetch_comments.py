#!/usr/bin/env python3
"""Fetch Reddit comments and dump raw `body` vs rendered `body_html` per comment.

Investigation / test-fixture tool for issue #179 (code-block formatting). It exposes the
difference between Reddit's two markdown layers:

  * `body`       — the raw markdown the author typed (identical on old & new Reddit).
  * `body_html`  — snudown's server-side render (old Reddit). Snudown is NOT CommonMark:
                   it treats ``` as inline code-span delimiters, never fenced code blocks.
                   New Reddit / Continuum render the same `body` with a CommonMark engine,
                   which is why they show code boxes and Slide (which uses body_html) does
                   not.

Auth uses the *userless* "installed_client" OAuth grant (no user login), with RedReader's
public installed-app client id. The oauth.reddit.com host + bearer token avoids the
anonymous .json rate-block.

Usage:
    reddit_fetch_comments.py <permalink-or-url> [--json OUT] [--limit N]
                             [--client-id ID] [--user-agent UA]

Examples:
    reddit_fetch_comments.py https://old.reddit.com/r/test/comments/1k65ysa/_/mop5hpi
    reddit_fetch_comments.py /r/test/comments/1k65ysa --json corpus.json --limit 100
"""
import argparse
import base64
import json
import sys
import urllib.parse
import urllib.request
import uuid

DEFAULT_CLIENT_ID = "yH0aTnJEt6qUgGn835B4vg"          # RedReader installed-app client id
DEFAULT_USER_AGENT = "org.quantumbadger.redreader/1.25.2"

TOKEN_URL = "https://www.reddit.com/api/v1/access_token"
OAUTH_HOST = "https://oauth.reddit.com"
INSTALLED_GRANT = "https://oauth.reddit.com/grants/installed_client"


def get_token(client_id, user_agent):
    """Fetch a userless bearer token via the installed_client grant."""
    device_id = uuid.uuid4().hex[:30]
    data = urllib.parse.urlencode(
        {"grant_type": INSTALLED_GRANT, "device_id": device_id}
    ).encode()
    req = urllib.request.Request(TOKEN_URL, data=data)
    # HTTP basic auth: client id as username, empty password.
    creds = base64.b64encode(f"{client_id}:".encode()).decode()
    req.add_header("Authorization", f"Basic {creds}")
    req.add_header("User-Agent", user_agent)
    with urllib.request.urlopen(req) as resp:
        token = json.load(resp).get("access_token")
    if not token:
        sys.exit("error: no access_token returned (check client id / network)")
    return token


def permalink_to_path(arg):
    """Normalize a full URL or bare permalink to a /r/.../comments/... path."""
    if arg.startswith("http"):
        arg = urllib.parse.urlparse(arg).path
    return "/" + arg.strip("/")


def fetch(path, token, user_agent, limit):
    url = f"{OAUTH_HOST}{path}.json?raw_json=1&limit={limit}"
    req = urllib.request.Request(url)
    req.add_header("Authorization", f"bearer {token}")
    req.add_header("User-Agent", user_agent)
    with urllib.request.urlopen(req) as resp:
        return json.load(resp)


def walk(node, out):
    """Depth-first collect (author, body, body_html) from a comment listing."""
    kind = node.get("kind")
    if kind == "Listing":
        for child in node["data"]["children"]:
            walk(child, out)
    elif kind == "t1":
        data = node["data"]
        out.append(
            {
                "author": data.get("author"),
                "body": data.get("body"),
                "body_html": data.get("body_html"),
                "created_utc": data.get("created_utc"),
                "name": data.get("name"),
            }
        )
        replies = data.get("replies")
        if isinstance(replies, dict):
            walk(replies, out)


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("permalink", help="comment/thread URL or /r/.../comments/... path")
    ap.add_argument("--json", metavar="OUT", help="write collected comments to this file")
    ap.add_argument("--limit", type=int, default=50)
    ap.add_argument("--client-id", default=DEFAULT_CLIENT_ID)
    ap.add_argument("--user-agent", default=DEFAULT_USER_AGENT)
    args = ap.parse_args()

    token = get_token(args.client_id, args.user_agent)
    path = permalink_to_path(args.permalink)
    listings = fetch(path, token, args.user_agent, args.limit)

    comments = []
    for listing in listings:
        walk(listing, comments)

    for i, c in enumerate(comments):
        print(f"\n===== COMMENT {i} by {c['author']} =====")
        print("--- body (raw markdown) ---")
        print(repr(c["body"]))
        print("--- body_html (snudown) ---")
        print(repr(c["body_html"]))

    if args.json:
        with open(args.json, "w") as f:
            json.dump(comments, f, indent=2)
        print(f"\nwrote {len(comments)} comments to {args.json}")


if __name__ == "__main__":
    main()

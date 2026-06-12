#!/usr/bin/env python3
"""Generate llms.txt (curated index) and llms-full.txt (full content) at the repo root
from the English GitBook tree, in SUMMARY.md order.

llms.txt follows https://llmstxt.org: H1, a one-paragraph blockquote, then the SUMMARY's
sections as H2s with `- [Title](url): description` entries. Descriptions are the first
prose sentence of each page, so they stay fresh without hand curation.

llms-full.txt is the entire guide concatenated in reading order — the single-file context
an AI assistant can ingest whole.

Usage: python3 scripts/generate-llms-txt.py   (from anywhere; paths resolve from this file)
"""

from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
GUIDE = REPO_ROOT / "docs" / "guide-en"
BLOB_BASE = "https://github.com/sahsenvar/KMapper/blob/main/docs/guide-en"

HEADER_SUMMARY = (
    "KMapper is a compile-time object mapping library for Kotlin Multiplatform (KSP, no "
    "reflection). Generated mappers return Result<T> with path-carrying typed errors; a "
    "fallback ladder (value > constructor default > null > error) contains bad wire data "
    "while reporting every absorbed error to an observability sink; lossy conversions are "
    "refused at compile time. Maven coordinates: io.github.sahsenvar:kmapper-core / "
    "kmapper-annotations / kmapper-compiler (+ converter/validator add-ons)."
)

LINK_RE = re.compile(r"^\*\s+\[([^\]]+)\]\(([^)]+)\)\s*$")
SECTION_RE = re.compile(r"^##\s+(.+)$")


def parse_summary() -> list[tuple[str, list[tuple[str, str]]]]:
    """SUMMARY.md -> [(section_title, [(page_title, rel_path), ...]), ...] in order."""
    sections: list[tuple[str, list[tuple[str, str]]]] = []
    current = "Overview"
    pages: list[tuple[str, str]] = []
    for line in (GUIDE / "SUMMARY.md").read_text(encoding="utf-8").splitlines():
        section = SECTION_RE.match(line.strip())
        if section:
            if pages:
                sections.append((current, pages))
            current, pages = section.group(1).strip(), []
            continue
        link = LINK_RE.match(line.strip())
        if link:
            pages.append((link.group(1).strip(), link.group(2).strip()))
    if pages:
        sections.append((current, pages))
    return sections


def first_sentence(markdown: str) -> str:
    """First prose sentence of a page: skip headings, blockquotes, code fences, tables.

    Wrapped paragraphs are joined before the sentence is extracted, so a sentence spanning
    several physical lines comes out whole.
    """
    in_fence = False
    paragraph: list[str] = []
    for raw in markdown.splitlines() + [""]:  # trailing blank flushes the last paragraph
        line = raw.strip()
        if line.startswith("```"):
            in_fence = not in_fence
            continue
        # Skip structure lines; "- "/"* " are list bullets, but "**bold** prose" is a paragraph.
        if in_fence or line.startswith(("#", ">", "|", "- ", "* ", "!")):
            continue
        if line:
            paragraph.append(line)
            continue
        if paragraph:  # blank line: paragraph complete
            text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", " ".join(paragraph))  # unlink
            text = re.sub(r"[`*_]", "", text)  # unstyle
            match = re.match(r"(.+?[.!?])(\s|$)", text)
            return (match.group(1) if match else text).strip()
    return ""


def main() -> None:
    sections = parse_summary()

    index_lines = ["# KMapper", "", f"> {HEADER_SUMMARY}", ""]
    full_lines = [
        "# KMapper — complete documentation (single file for LLM ingestion)",
        "",
        f"> {HEADER_SUMMARY}",
        "",
        "Generated from docs/guide-en in reading order by scripts/generate-llms-txt.py.",
        "",
    ]

    for section, pages in sections:
        index_lines += [f"## {section}", ""]
        for title, rel_path in pages:
            page = GUIDE / rel_path
            content = page.read_text(encoding="utf-8")
            description = first_sentence(content)
            index_lines.append(f"- [{title}]({BLOB_BASE}/{rel_path}): {description}")
            full_lines += [
                "",
                "=" * 78,
                f"SECTION: {section} — {title}  ({rel_path})",
                "=" * 78,
                "",
                content.strip(),
            ]
        index_lines.append("")

    index_lines += [
        "## Optional",
        "",
        f"- [Consumer cheat sheet for AI agents]({BLOB_BASE.rsplit('/', 1)[0]}/AGENTS.md): "
        "compact rules, annotation table, and compile-error fixes for coding agents.",
        "- [CHANGELOG](https://github.com/sahsenvar/KMapper/blob/main/CHANGELOG.md): "
        "release history including the 1.x -> 2.0 breaking redesign.",
        "",
    ]

    (REPO_ROOT / "llms.txt").write_text("\n".join(index_lines), encoding="utf-8")
    (REPO_ROOT / "llms-full.txt").write_text("\n".join(full_lines) + "\n", encoding="utf-8")
    page_count = sum(len(pages) for _, pages in sections)
    print(f"llms.txt: {page_count} pages indexed; llms-full.txt: {(REPO_ROOT / 'llms-full.txt').stat().st_size} bytes")


if __name__ == "__main__":
    main()

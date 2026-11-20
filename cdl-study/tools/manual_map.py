#!/usr/bin/env python3
"""
Maps the Utah CDL manual PDF to the app's content files.

It writes one file per required section in app/src/main/assets/content, with the
subsection numbers, their titles and the PDF pages each one is on. The app needs
the page numbers to show you the right pages of your own copy of the manual.

IT COPIES NO MANUAL TEXT. The manual is AAMVA material, and permission to
reproduce it is given to state driver licence agencies only. The app reads the
copy you import yourself. This tool only records headings and page numbers.

A file that exists already keeps its facts, cards and questions. Only the
titles and the page numbers are refreshed. So you can write facts, get a new
edition of the manual, and run this again.

Usage:
    python3 tools/manual_map.py /path/to/CDL-Manual.pdf
    python3 tools/manual_map.py /path/to/CDL-Manual.pdf --sections 5,6

Needs pdftotext (poppler-utils).
"""

import argparse
import json
import os
import re
import subprocess
import sys

# The sections Utah requires for a Class A CDL with air brakes and no
# endorsements. Sections 4, 7, 8, 9 and 10 are endorsement material.
REQUIRED = {
    1: ("Introduction", "general_knowledge"),
    2: ("Driving Safely", "general_knowledge"),
    3: ("Transporting Cargo Safely", "general_knowledge"),
    5: ("Air Brakes", "air_brakes"),
    6: ("Combination Vehicles", "combination"),
    11: ("Vehicle Inspection", "inspection"),
    12: ("Basic Vehicle Control Skills Test", "basic_control"),
    13: ("On-Road Driving", "on_road"),
}

PAGE_WIDTH = 612
PAGE_HEIGHT = 792
# The manual is in two columns. A heading in the right column is not at the
# start of a text line, so each column is read on its own.
COLUMNS = ((0, 310), (300, 312))

HEADING = re.compile(r"^[ \t]*(\d{1,2}\.\d{1,2}(?:\.\d{1,2})?)[ \t]*[-–—]?[ \t]+(\S.*?)[ \t]*$")
FOOTER_SECTION = re.compile(r"^\s*Section\s+(\d+)\s*[-–]\s*(.+?)\s{2,}", re.M)
FOOTER_LABEL = re.compile(r"Page\s+(\d+-\d+)\s*$", re.M)


def run_pdftotext(pdf, page, x=None, width=None):
    cmd = ["pdftotext", "-f", str(page), "-l", str(page), "-layout"]
    if x is not None:
        cmd += ["-x", str(x), "-y", "0", "-W", str(width), "-H", str(PAGE_HEIGHT)]
    cmd += [pdf, "-"]
    done = subprocess.run(cmd, capture_output=True, text=True)
    if done.returncode != 0:
        sys.exit(f"pdftotext failed on page {page}: {done.stderr.strip()}")
    return done.stdout


def page_count(pdf):
    out = subprocess.run(["pdfinfo", pdf], capture_output=True, text=True).stdout
    found = re.search(r"^Pages:\s+(\d+)", out, re.M)
    if not found:
        sys.exit("pdfinfo gave no page count. Is this a PDF?")
    return int(found.group(1))


def section_ranges(pdf, pages):
    """The physical page range of every section, from the page footers."""
    ranges = {}
    current, start = None, None
    for page in range(1, pages + 1):
        found = FOOTER_SECTION.findall(run_pdftotext(pdf, page))
        number = int(found[-1][0]) if found else None
        if number != current:
            if current is not None:
                ranges[current] = (start, page - 1)
            current, start = number, page
    if current is not None:
        ranges[current] = (start, pages)
    return ranges


def is_blank(pdf, page):
    """True for a page that only says it is left blank on purpose."""
    return "INTENTIONALLY LEFT BLANK" in run_pdftotext(pdf, page).upper()


def page_label(pdf, page):
    found = FOOTER_LABEL.findall(run_pdftotext(pdf, page))
    return found[-1] if found else None


def headings_on_page(pdf, page, section):
    """Every subsection heading on one page, read column by column."""
    out = []
    for x, width in COLUMNS:
        text = run_pdftotext(pdf, page, x, width)
        for line in text.splitlines():
            match = HEADING.match(line)
            if not match:
                continue
            number, title = match.group(1), match.group(2)
            if not number.startswith(f"{section}."):
                continue
            # A footer or a page label is not a heading.
            if title.lower().startswith("page"):
                continue
            out.append((number, title.strip()))
    return out


def sort_key(number):
    return tuple(int(part) for part in number.split("."))


def build_section(pdf, number, title, track, first, last):
    # A blank page at the end of a section is not reading.
    while last > first and is_blank(pdf, last):
        last -= 1

    found = {}
    for page in range(first, last + 1):
        for sub_number, sub_title in headings_on_page(pdf, page, number):
            if sub_number not in found:
                found[sub_number] = {"title": sub_title, "page": page}

    ordered = sorted(found.items(), key=lambda kv: sort_key(kv[0]))

    # The pages before the first numbered heading hold the section opening,
    # such as the CDL classes in Section 1. They are reading too, so they get
    # a subsection of their own at N.0.
    if ordered and ordered[0][1]["page"] > first:
        ordered.insert(0, (f"{number}.0", {
            "title": "What This Section Covers",
            "page": first,
        }))
    elif not ordered:
        ordered = [(f"{number}.0", {"title": "What This Section Covers", "page": first})]

    subsections = []
    for index, (sub_number, data) in enumerate(ordered):
        start = data["page"]
        end = ordered[index + 1][1]["page"] if index + 1 < len(ordered) else last
        if end < start:
            end = start
        while end > start and is_blank(pdf, end):
            end -= 1
        labels = [page_label(pdf, p) for p in (start, end)]
        labels = [l for l in labels if l]
        subsections.append({
            "id": sub_number,
            "title": data["title"],
            "pdfPages": f"{start}-{end}" if end != start else str(start),
            "manualPages": " to ".join(dict.fromkeys(labels)) if labels else None,
            "facts": [],
            "cards": [],
            "exam": [],
        })

    return {
        "section": number,
        "title": title,
        "track": track,
        "readingPages": last - first + 1,
        "pdfPages": f"{first}-{last}",
        "subsections": subsections,
        "exam": [],
    }


def merge(fresh, old):
    """Keeps the facts, cards and questions that are written already."""
    if not old:
        return fresh
    by_id = {sub.get("id"): sub for sub in old.get("subsections", [])}
    for sub in fresh["subsections"]:
        kept = by_id.get(sub["id"])
        if not kept:
            continue
        for field in ("facts", "cards", "exam"):
            if kept.get(field):
                sub[field] = kept[field]
        # A title corrected by hand wins: the PDF wraps long headings.
        if kept.get("titleFixed"):
            sub["title"] = kept["title"]
            sub["titleFixed"] = True
    if old.get("exam"):
        fresh["exam"] = old["exam"]
    return fresh


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pdf", help="the Utah CDL manual PDF")
    parser.add_argument("--sections", help="only these section numbers, such as 5,6")
    parser.add_argument(
        "--out",
        default=os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                             "app/src/main/assets/content"),
        help="where the content files go",
    )
    args = parser.parse_args()

    wanted = REQUIRED
    if args.sections:
        keep = {int(n) for n in args.sections.split(",")}
        wanted = {n: v for n, v in REQUIRED.items() if n in keep}

    pages = page_count(args.pdf)
    print(f"{args.pdf}: {pages} pages")
    ranges = section_ranges(args.pdf, pages)
    missing = [n for n in wanted if n not in ranges]
    if missing:
        sys.exit(f"These sections were not found in the PDF footers: {missing}. "
                 "Is this the Utah CDL manual?")

    os.makedirs(args.out, exist_ok=True)
    for number, (title, track) in sorted(wanted.items()):
        first, last = ranges[number]
        section = build_section(args.pdf, number, title, track, first, last)
        path = os.path.join(args.out, f"section{number:02d}.json")
        old = None
        if os.path.exists(path):
            with open(path) as handle:
                old = json.load(handle)
        section = merge(section, old)
        with open(path, "w") as handle:
            json.dump(section, handle, indent=2, ensure_ascii=False)
            handle.write("\n")
        facts = sum(len(s["facts"]) for s in section["subsections"])
        print(f"  section {number:>2}  pages {first}-{last}  "
              f"{len(section['subsections'])} subsections  {facts} facts  -> {os.path.basename(path)}")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Add single-line borders to every table in a docx file (in-place)."""

import sys
from docx import Document
from docx.oxml.ns import qn
from docx.oxml import OxmlElement


def _add_borders(table):
    tbl = table._tbl
    tblPr = tbl.find(qn('w:tblPr'))
    if tblPr is None:
        tblPr = OxmlElement('w:tblPr')
        tbl.insert(0, tblPr)

    existing = tblPr.find(qn('w:tblBorders'))
    if existing is not None:
        tblPr.remove(existing)

    tblBorders = OxmlElement('w:tblBorders')
    for side in ('top', 'left', 'bottom', 'right', 'insideH', 'insideV'):
        el = OxmlElement(f'w:{side}')
        el.set(qn('w:val'), 'single')
        el.set(qn('w:sz'), '4')
        el.set(qn('w:space'), '0')
        el.set(qn('w:color'), 'auto')
        tblBorders.append(el)

    tblPr.append(tblBorders)


def main():
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} <file.docx>", file=sys.stderr)
        sys.exit(1)

    path = sys.argv[1]
    doc = Document(path)
    for table in doc.tables:
        _add_borders(table)
    doc.save(path)


if __name__ == '__main__':
    main()

"""Word 交付文档通用排版组件：A4、2.5cm 边距、黑体标题、宋体正文、浅灰表头、页码。"""
import re
from docx import Document
from docx.enum.section import WD_SECTION
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor

BODY_FONT = "宋体"
HEAD_FONT = "黑体"
HEADER_FILL = "D9D9D9"
EMOJI = re.compile("[\U0001F000-\U0001FAFF\u2600-\u27BF\uFE0F\u2B00-\u2BFF]")


def set_run_font(run, name=BODY_FONT, size=12, bold=False, italic=False):
    run.font.name = name
    run.font.size = Pt(size)
    run.bold = bold
    run.italic = italic
    run.font.color.rgb = RGBColor(0, 0, 0)
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.find(qn("w:rFonts"))
    if rfonts is None:
        rfonts = OxmlElement("w:rFonts")
        rpr.append(rfonts)
    rfonts.set(qn("w:ascii"), name)
    rfonts.set(qn("w:hAnsi"), name)
    rfonts.set(qn("w:eastAsia"), name)
    rfonts.set(qn("w:cs"), name)


def setup_document(title):
    doc = Document()
    sec = doc.sections[0]
    sec.page_width = Cm(21)
    sec.page_height = Cm(29.7)
    sec.top_margin = Cm(2.5)
    sec.bottom_margin = Cm(2.5)
    sec.left_margin = Cm(2.5)
    sec.right_margin = Cm(2.5)
    sec.header_distance = Cm(1.5)
    sec.footer_distance = Cm(1.5)

    styles = doc.styles
    normal = styles["Normal"]
    normal.font.name = BODY_FONT
    normal.font.size = Pt(12)
    normal.font.color.rgb = RGBColor(0, 0, 0)
    normal.element.rPr.rFonts.set(qn("w:eastAsia"), BODY_FONT)
    pf = normal.paragraph_format
    pf.line_spacing = 1.5
    pf.space_before = Pt(0)
    pf.space_after = Pt(0)

    for level, size in ((1, 18), (2, 15), (3, 13), (4, 12)):
        st = styles[f"Heading {level}"]
        st.font.name = HEAD_FONT
        st.font.size = Pt(size)
        st.font.bold = True
        st.font.color.rgb = RGBColor(0, 0, 0)
        st.element.rPr.rFonts.set(qn("w:eastAsia"), HEAD_FONT)
        st.paragraph_format.space_before = Pt(12 if level == 1 else 8)
        st.paragraph_format.space_after = Pt(6)
        st.paragraph_format.keep_with_next = True
        st.paragraph_format.line_spacing = 1.3

    doc.core_properties.title = title
    doc.core_properties.author = "婚礼影像数字化多端 SaaS 平台项目组"
    doc.core_properties.subject = title
    return doc


def add_header_footer(doc, doc_title, version):
    sec = doc.sections[0]
    hp = sec.header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.LEFT
    r = hp.add_run(f"{doc_title}    {version}")
    set_run_font(r, BODY_FONT, 9)
    # 页眉下划线
    ppr = hp._p.get_or_add_pPr()
    pbdr = OxmlElement("w:pBdr")
    bottom = OxmlElement("w:bottom")
    bottom.set(qn("w:val"), "single")
    bottom.set(qn("w:sz"), "4")
    bottom.set(qn("w:space"), "1")
    bottom.set(qn("w:color"), "808080")
    pbdr.append(bottom)
    ppr.append(pbdr)

    fp = sec.footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r1 = fp.add_run("第 ")
    set_run_font(r1, BODY_FONT, 9)
    _field(fp, "PAGE")
    r2 = fp.add_run(" 页  共 ")
    set_run_font(r2, BODY_FONT, 9)
    _field(fp, "NUMPAGES")
    r3 = fp.add_run(" 页")
    set_run_font(r3, BODY_FONT, 9)


def _field(paragraph, instr):
    run = paragraph.add_run()
    set_run_font(run, BODY_FONT, 9)
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = f" {instr} "
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    txt = OxmlElement("w:t")
    txt.text = "1"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(it)
    run._r.append(fld_sep)
    run._r.append(txt)
    run._r.append(fld_end)


def cover(doc, title, subtitle, version, org, date_text):
    for _ in range(3):
        add_para(doc, "", indent=False)
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(18)
    r = p.add_run(title)
    set_run_font(r, HEAD_FONT, 26, True)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(60)
    r = p.add_run(subtitle)
    set_run_font(r, HEAD_FONT, 22, True)

    for text in (f"文档版本：{version}", f"编制单位：{org}", f"编制日期：{date_text}"):
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_after = Pt(8)
        r = p.add_run(text)
        set_run_font(r, BODY_FONT, 14)

    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(80)
    r = p.add_run("内部资料  注意保密")
    set_run_font(r, BODY_FONT, 10.5)
    doc.add_page_break()


def revision_table(doc, rows=None):
    heading(doc, "修订记录", 1)
    rows = rows or [("V1.0", "2026-11-13", "初版，按 PRO 与上线检查表编制", "项目组", "项目经理")]
    table(doc, ["版本", "日期", "修订内容", "编制", "审核"], rows, widths=[2.2, 3.0, 7.0, 2.4, 2.4], font_size=10.5)


def toc(doc):
    heading(doc, "目录", 1)
    p = doc.add_paragraph()
    run = p.add_run()
    fld_begin = OxmlElement("w:fldChar")
    fld_begin.set(qn("w:fldCharType"), "begin")
    it = OxmlElement("w:instrText")
    it.set(qn("xml:space"), "preserve")
    it.text = ' TOC \\o "1-3" \\h \\z \\u '
    fld_sep = OxmlElement("w:fldChar")
    fld_sep.set(qn("w:fldCharType"), "separate")
    t = OxmlElement("w:t")
    t.text = "右键单击此处，选择“更新域”生成目录。"
    fld_end = OxmlElement("w:fldChar")
    fld_end.set(qn("w:fldCharType"), "end")
    run._r.append(fld_begin)
    run._r.append(it)
    run._r.append(fld_sep)
    run._r.append(t)
    run._r.append(fld_end)
    p2 = doc.add_paragraph()
    r = p2.add_run("提示：在 Word 中右键目录区域，选择“更新域”并勾选“更新整个目录”，即可显示页码。")
    set_run_font(r, BODY_FONT, 9)
    doc.add_page_break()


def heading(doc, text, level=1):
    p = doc.add_heading(level=level)
    run = p.add_run(text)
    set_run_font(run, HEAD_FONT, {1: 18, 2: 15, 3: 13, 4: 12}[level], True)
    return p


def add_para(doc, text, bold=False, indent=True, size=12, align=None, space_after=3):
    p = doc.add_paragraph()
    pf = p.paragraph_format
    pf.first_line_indent = Pt(size * 2) if indent else Pt(0)
    pf.space_after = Pt(space_after)
    pf.line_spacing = 1.5
    if align == "center":
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    elif align == "right":
        p.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    else:
        p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    r = p.add_run(text)
    set_run_font(r, BODY_FONT, size, bold)
    return p


def bullet(doc, text, level=0):
    p = doc.add_paragraph(style="List Bullet" if level == 0 else "List Bullet 2")
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(text)
    set_run_font(r, BODY_FONT, 12)
    return p


def numbered(doc, text):
    p = doc.add_paragraph(style="List Number")
    p.paragraph_format.line_spacing = 1.5
    p.paragraph_format.space_after = Pt(2)
    r = p.add_run(text)
    set_run_font(r, BODY_FONT, 12)
    return p


def table(doc, headers, rows, widths=None, font_size=10.5, header=True):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    hdr = t.rows[0]
    for i, h in enumerate(headers):
        cell = hdr.cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.line_spacing = 1.15
        r = p.add_run(str(h))
        set_run_font(r, HEAD_FONT, font_size, True)
        _shade(cell, HEADER_FILL)
    _repeat_header(hdr)
    for row in rows:
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cell = cells[i]
            cell.text = ""
            p = cell.paragraphs[0]
            p.paragraph_format.line_spacing = 1.25
            p.paragraph_format.space_after = Pt(0)
            r = p.add_run("" if val is None else str(val))
            set_run_font(r, BODY_FONT, font_size)
    if widths:
        for row in t.rows:
            for i, w in enumerate(widths):
                row.cells[i].width = Cm(w)
    # 表后留白
    sp = doc.add_paragraph()
    sp.paragraph_format.space_after = Pt(3)
    return t


def _shade(cell, fill):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    tc_pr.append(shd)


def _repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    th = OxmlElement("w:tblHeader")
    th.set(qn("w:val"), "true")
    tr_pr.append(th)
    cant = OxmlElement("w:cantSplit")
    tr_pr.append(cant)


def note(doc, text):
    p = doc.add_paragraph()
    p.paragraph_format.line_spacing = 1.4
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(6)
    p.paragraph_format.left_indent = Cm(0.5)
    r = p.add_run("说明：" + text)
    set_run_font(r, BODY_FONT, 10.5)
    r.italic = True


def page_break(doc):
    doc.add_page_break()


def assert_no_emoji(doc):
    texts = []
    for p in doc.paragraphs:
        texts.append(p.text)
    for t in doc.tables:
        for row in t.rows:
            for cell in row.cells:
                texts.append(cell.text)
    for text in texts:
        if EMOJI.search(text):
            raise ValueError("文档包含 emoji：" + text[:80])

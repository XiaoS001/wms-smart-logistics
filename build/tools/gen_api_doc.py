# -*- coding: utf-8 -*-
"""从 Controller 源码自动提取接口清单，生成《系统接口文档》DOCX。

这是可复用工具，不是一次性脚本：接口变更后重新运行即可同步文档。
位置：build/tools/gen_api_doc.py

解析策略（比单条大正则稳健得多）：
1. 用正则定位全部方法级映射注解 @Get/Post/Put/DeleteMapping 的位置；
2. 从注解起点用括号配对取出完整注解体，解析出路径；
3. 从注解终点向后扫描，跨过空白与注释，取出方法签名（到第一个 '{' 为止）；
4. 从注解起点向前扫描，跨过空白与注释，若紧邻 @Operation 则解析其
   summary/description（该注解可跨多行，不能用不带 DOTALL 的正则）。
5. 没有 @Operation 的接口同样要进文档（如各模块的列表接口、健康检查），
   否则文档会漏掉真实的接口。

用法：
    python build/tools/gen_api_doc.py
"""
import io
import os
import re
import sys
import time

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Mm, Pt

ROOT = r"D:\DeepSeekXM\XM01"
CTRL_DIR = os.path.join(ROOT, "03-源码", "backend", "src", "main", "java",
                        "com", "lijunjie", "wms", "controller")
OUT_DIR = os.path.join(ROOT, "02-设计文档", "02-概要设计")

SONG, HEI, TNR = "宋体", "黑体", "Times New Roman"


def set_run_font(run, cn_font=SONG, size_pt=12, bold=False, ascii_font=TNR):
    run.font.name = ascii_font
    run.font.size = Pt(size_pt)
    run.bold = bold
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), ascii_font)
    rFonts.set(qn("w:hAnsi"), ascii_font)
    rFonts.set(qn("w:eastAsia"), cn_font)


def set_para(p, indent_chars=2, line_spacing=1.25, before_lines=0.0, after_lines=0.0, align=None):
    pf = p.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
    pf.line_spacing = line_spacing
    pPr = p._element.get_or_add_pPr()
    ind = pPr.find(qn("w:ind"))
    if indent_chars:
        if ind is None:
            ind = OxmlElement("w:ind")
            pPr.append(ind)
        ind.set(qn("w:firstLineChars"), str(int(indent_chars * 100)))
        ind.set(qn("w:firstLine"), str(int(indent_chars * 240)))
    elif ind is not None:
        pPr.remove(ind)
    pf.space_before = Pt(before_lines * 15)
    pf.space_after = Pt(after_lines * 15)
    if align is not None:
        pf.alignment = align
    return p


def H(doc, text, level):
    size, font = {1: (18, HEI), 2: (14, HEI), 3: (12, HEI)}[level]
    p = doc.add_paragraph()
    set_para(p, indent_chars=0, before_lines=0.5, after_lines=0.5,
             align=WD_ALIGN_PARAGRAPH.CENTER if level == 1 else WD_ALIGN_PARAGRAPH.LEFT)
    set_run_font(p.add_run(text), cn_font=font, size_pt=size, bold=True)
    return p


def B(doc, text, indent=2):
    p = doc.add_paragraph()
    set_para(p, indent_chars=indent, align=WD_ALIGN_PARAGRAPH.JUSTIFY)
    set_run_font(p.add_run(text))
    return p


def CODE(doc, text):
    p = doc.add_paragraph()
    set_para(p, indent_chars=0, align=WD_ALIGN_PARAGRAPH.LEFT, after_lines=0.2)
    set_run_font(p.add_run(text), cn_font=SONG, size_pt=10.5, ascii_font="Consolas")
    return p


def CAP(doc, text, size=10.5):
    p = doc.add_paragraph()
    set_para(p, indent_chars=0, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_run_font(p.add_run(text), size_pt=size)
    return p


def tbl_borders(table, header_rows=1):
    tblPr = table._tbl.tblPr
    for el in tblPr.findall(qn("w:tblBorders")):
        tblPr.remove(el)
    b = OxmlElement("w:tblBorders")
    for tag, sz in (("top", "8"), ("bottom", "8")):
        e = OxmlElement("w:" + tag)
        e.set(qn("w:val"), "single"); e.set(qn("w:sz"), sz); e.set(qn("w:color"), "000000")
        b.append(e)
    for tag in ("left", "right", "insideH", "insideV"):
        e = OxmlElement("w:" + tag)
        e.set(qn("w:val"), "none"); e.set(qn("w:sz"), "0")
        b.append(e)
    tblPr.append(b)
    for r in range(header_rows):
        for cell in table.rows[r].cells:
            tcPr = cell._tc.get_or_add_tcPr()
            for el in tcPr.findall(qn("w:tcBorders")):
                tcPr.remove(el)
            tb = OxmlElement("w:tcBorders")
            e = OxmlElement("w:bottom")
            e.set(qn("w:val"), "single"); e.set(qn("w:sz"), "8"); e.set(qn("w:color"), "000000")
            tb.append(e)
            tcPr.append(tb)


def T(doc, caption, headers, rows, widths=None, size=9):
    if caption:
        CAP(doc, caption)
    t = doc.add_table(rows=1 + len(rows), cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    for j, h in enumerate(headers):
        c = t.rows[0].cells[j]; c.text = ""
        p = c.paragraphs[0]; set_para(p, indent_chars=0, align=WD_ALIGN_PARAGRAPH.CENTER)
        set_run_font(p.add_run(h), cn_font=HEI, size_pt=size)
    for i, row in enumerate(rows, start=1):
        for j, v in enumerate(row):
            c = t.rows[i].cells[j]; c.text = ""
            p = c.paragraphs[0]
            set_para(p, indent_chars=0, align=WD_ALIGN_PARAGRAPH.LEFT)
            set_run_font(p.add_run(str(v)), size_pt=size)
    tbl_borders(t, 1)
    if widths:
        for j, w in enumerate(widths):
            for row in t.rows:
                row.cells[j].width = Cm(w)
    sp = doc.add_paragraph(); set_para(sp, indent_chars=0)
    return t


# --------------------------------------------------------------------------
# 解析
# --------------------------------------------------------------------------
def annotation_body(text, start):
    """从注解名起点取到配对的右括号，返回 (完整文本, 右括号后一位下标)"""
    i = text.index("(", start)
    depth = 0
    j = i
    while j < len(text):
        ch = text[j]
        if ch == '"':
            j += 1
            while j < len(text) and text[j] != '"':
                j += 2 if text[j] == '\\' else 1
        elif ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return text[start:j + 1], j + 1
        j += 1
    return text[start:], len(text)


def skip_backward(text, pos):
    """从 pos 向前跳过空白与整段注释（含 Javadoc）"""
    i = pos
    while i > 0:
        if text[i - 1].isspace():
            i -= 1
            continue
        if text[max(0, i - 2):i] == "*/":
            k = text.rfind("/*", 0, i)
            if k >= 0:
                i = k
                continue
        # 行注释：向前找到 // 且该行内没有其他内容
        line_start = text.rfind("\n", 0, i) + 1
        line = text[line_start:i]
        if "//" in line and line.strip().startswith("//"):
            i = line_start
            continue
        break
    return i


def skip_forward(text, pos):
    i = pos
    while i < len(text):
        if text[i].isspace():
            i += 1
            continue
        if text[i:i + 2] == "//":
            i = text.find("\n", i)
            if i < 0:
                return len(text)
            continue
        if text[i:i + 2] == "/*":
            k = text.find("*/", i)
            if k < 0:
                return len(text)
            i = k + 2
            continue
        break
    return i


MAPPING_RE = re.compile(r'@(Get|Post|Put|Delete)Mapping\b')
CLASS_MAPPING_RE = re.compile(r'@RequestMapping\s*\(([^)]*)\)')


def parse_controller(path):
    with io.open(path, "r", encoding="utf-8") as fh:
        text = fh.read()
    class_name = os.path.splitext(os.path.basename(path))[0]

    prefix = ""
    decl = re.search(r'public\s+class\s+' + class_name, text)
    head = text[:decl.start()] if decl else text
    class_maps = CLASS_MAPPING_RE.findall(head)
    if class_maps:
        m = re.search(r'"([^"]*)"', class_maps[-1])
        if m:
            prefix = m.group(1)

    endpoints = []
    for mm in MAPPING_RE.finditer(text):
        verb = mm.group(1).upper()
        # 映射注解可能不带参数（如 @GetMapping 用于类级基础路径），此时括号不存在，
        # 必须单独处理：否则这类接口会被整段跳过，文档出现缺失。
        after = skip_forward(text, mm.end())
        if after < len(text) and text[after] == "(":
            anno_text, anno_end = annotation_body(text, after)
        else:
            anno_text, anno_end = mm.group(0), mm.end()
        pm = re.search(r'"([^"]*)"', anno_text)
        sub_path = pm.group(1) if pm else ""

        before = skip_backward(text, mm.start())
        summary = description = ""
        has_operation = False
        if before >= 10 and text[max(0, before - 11):before].endswith("@Operation"):
            has_operation = True
            op_text, _ = annotation_body(text, before - len("@Operation"))
            s = re.search(r'summary\s*=\s*"([^"]*)"', op_text, re.S)
            if s:
                summary = s.group(1)
            d = re.search(r'description\s*=\s*"([^"]*)"', op_text, re.S)
            if d:
                description = re.sub(r'\s+', ' ', d.group(1)).strip()

        sig_start = skip_forward(text, anno_end)
        brace = text.find("{", sig_start)
        signature = text[sig_start:brace] if brace > 0 else ""
        nm = re.search(r'(\w+)\s*\(', signature)
        method_name = nm.group(1) if nm else "(unknown)"
        params_text = signature[signature.find("(") + 1:] if "(" in signature else ""

        params = []
        for pm2 in re.finditer(r'@RequestParam\s*\(([^)]*)\)\s*(?:final\s+)?([\w<>\[\], .]+?)\s+(\w+)', params_text):
            args, ptype, pname = pm2.group(1), pm2.group(2).strip(), pm2.group(3)
            required = "可选" if "required = false" in args else "必填"
            default = ""
            dm = re.search(r'defaultValue\s*=\s*"([^"]*)"', args)
            if dm:
                default = dm.group(1)
            params.append((pname, ptype, "查询参数", required + ("（默认 " + default + "）" if default else "")))
        named = set()
        for pm2 in re.finditer(r'@PathVariable\s*\(\s*"([^"]+)"\s*\)', params_text):
            params.append((pm2.group(1), "Long", "路径变量", "必填"))
            named.add(pm2.group(1))
        for pm2 in re.finditer(r'@PathVariable\s+(?!\()[\w<>\[\], .]*?\s+(\w+)', params_text):
            if pm2.group(1) not in named:
                params.append((pm2.group(1), "Long", "路径变量", "必填"))
                named.add(pm2.group(1))
        for pm2 in re.finditer(r'@RequestBody\s*(?:\([^)]*\))?\s*(?:final\s+)?([\w<>\[\], .]+?)\s+(\w+)', params_text):
            params.append((pm2.group(2), pm2.group(1).strip(), "请求体 JSON", "必填"))

        if not has_operation:
            # 无 @Operation 的接口也必须进文档，否则会漏掉列表类接口
            summary = "接口：%s %s（处理方法 %s）" % (verb, prefix + sub_path, method_name)

        endpoints.append({
            "verb": verb,
            "path": prefix + sub_path,
            "name": method_name,
            "summary": summary,
            "description": description,
            "params": params,
            "hasOperation": has_operation,
        })
    return class_name, prefix, endpoints


MODULE_NAMES = {
    "HealthController": "系统健康检查",
    "AuthController": "认证与登录",
    "UserController": "用户与权限管理",
    "WarehouseController": "仓库管理",
    "LocationController": "库位管理",
    "ProductController": "商品管理",
    "CategoryController": "商品分类",
    "InboundController": "入库管理",
    "OutboundController": "出库管理",
    "StockController": "库存管理",
    "SlottingController": "货位分配算法与优化方案",
    "ReportController": "报表统计",
}
MODULE_ORDER = [
    "HealthController", "AuthController", "UserController",
    "WarehouseController", "LocationController", "ProductController", "CategoryController",
    "InboundController", "OutboundController", "StockController",
    "SlottingController", "ReportController",
]


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    out = os.path.join(OUT_DIR, "系统接口文档-v1-%s.docx" % time.strftime("%Y%m%d"))

    controllers = []
    for name in MODULE_ORDER:
        fp = os.path.join(CTRL_DIR, name + ".java")
        if os.path.exists(fp):
            controllers.append(parse_controller(fp))
    total = sum(len(c[2]) for c in controllers)

    doc = Document()
    for s in doc.sections:
        s.page_width, s.page_height = Mm(210), Mm(297)
        s.top_margin, s.bottom_margin = Mm(30), Mm(25)
        s.left_margin, s.right_margin = Mm(25), Mm(25)
        s.header_distance, s.footer_distance = Mm(20), Mm(15)
    st = doc.styles["Normal"]
    st.font.name = TNR
    st.font.size = Pt(12)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), SONG)

    p = doc.add_paragraph(); set_para(p, indent_chars=0, before_lines=1, after_lines=0.5, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_run_font(p.add_run("面向智慧物流的仓储管理系统"), cn_font=HEI, size_pt=16, bold=True)
    p = doc.add_paragraph(); set_para(p, indent_chars=0, after_lines=1, align=WD_ALIGN_PARAGRAPH.CENTER)
    set_run_font(p.add_run("系统接口文档"), cn_font=HEI, size_pt=16, bold=True)

    T(doc, "表 0.1　文档信息", ["项目", "内容"],
      [["文档名称", "系统接口文档"], ["版本", "v1.0"],
       ["编制日期", time.strftime("%Y 年 %m 月 %d 日")],
       ["接口总数", "%d 个（覆盖 %d 个控制器）" % (total, len(controllers))],
       ["生成方式", "由 Controller 源码注解自动提取（build/tools/gen_api_doc.py），保证与实现同源"],
       ["对应论文", "第 5 章 系统设计与实现（接口设计部分）"],
       ["接口风格", "RESTful；统一响应体 Result{code, message, data, success}"],
       ["统一约定", "HTTP 状态码与响应体 code 保持一致；异常统一由全局处理器转换为可读提示"]],
      widths=[3.0, 12.0])

    H(doc, "第一章　通用约定", 1)
    H(doc, "1.1　请求与响应格式", 2)
    B(doc, "所有接口以 JSON 交换数据，涉及请求体的接口需声明 Content-Type: application/json。响应体统一为如下结构：")
    CODE(doc, '{ "code": 200, "message": "操作成功", "data": { ... }, "success": true, "timestamp": 1791711398520 }')
    B(doc, "其中 code 为业务状态码，message 为可直接展示给用户的提示，data 为业务数据，"
           "success 为布尔快捷判断，timestamp 为服务端时间戳。")

    H(doc, "1.2　状态码约定", 2)
    T(doc, "表 1.1　业务状态码与 HTTP 状态码对照", ["业务码", "HTTP", "含义", "典型场景"],
      [["200", "200", "成功", "查询、新增、修改、删除成功"],
       ["400", "400", "请求参数不合法", "必填项为空、数量为 0、策略编码不存在、请求体格式错误"],
       ["401", "401", "未认证或令牌无效", "未提供令牌、令牌被伪造或已过期、登录名或密码错误"],
       ["403", "403", "无权限", "账号被停用"],
       ["404", "404", "资源不存在", "仓库、商品、单据、优化方案不存在"],
       ["405", "405", "请求方法不支持", "用 POST 调用仅支持 GET 的接口"],
       ["409", "409", "业务冲突", "编码重复、库存不足、单据已完成不可重复确认、方案已应用"],
       ["415", "415", "内容类型不支持", "接口要求 JSON 但客户端发送表单或未声明类型"],
       ["500", "500", "服务器内部错误", "未预期的异常，响应体不暴露堆栈细节"]],
      widths=[1.6, 1.4, 3.2, 8.8])

    H(doc, "1.3　认证方式", 2)
    B(doc, "除 /api/health/** 与 /api/auth/login 外，其余接口均需在请求头携带令牌：")
    CODE(doc, 'Authorization: Bearer <token>')
    B(doc, "令牌由登录接口签发，包含用户名、角色与有效期信息。系统同时兼容不带 Bearer 前缀的写法。")

    H(doc, "1.4　接口总览", 1)
    T(doc, "表 1.2　接口清单总览",
      ["序号", "控制器", "所属模块", "接口数"],
      [[str(i + 1), c[0], MODULE_NAMES.get(c[0], c[0]), str(len(c[2]))] for i, c in enumerate(controllers)]
      + [["", "", "合计", str(total)]],
      widths=[1.4, 4.2, 6.0, 2.0])

    H(doc, "第二章　接口详细说明", 1)
    for idx, (class_name, prefix, endpoints) in enumerate(controllers):
        H(doc, "2.%d　%s" % (idx + 1, MODULE_NAMES.get(class_name, class_name)), 2)
        B(doc, "控制器：%s；基础路径：%s；接口数：%d。" % (class_name, prefix or "(无)", len(endpoints)))
        for eidx, ep in enumerate(endpoints):
            title = ep["summary"] if ep["hasOperation"] else ("%s %s" % (ep["verb"], ep["path"]))
            H(doc, "2.%d.%d　%s" % (idx + 1, eidx + 1, title), 3)
            CODE(doc, "%s %s" % (ep["verb"], ep["path"]))
            B(doc, "处理方法：%s()。" % ep["name"])
            if ep["description"]:
                B(doc, "接口说明：" + ep["description"])
            if ep["params"]:
                T(doc, None, ["参数名", "类型", "位置", "是否必填"],
                  [[pp[0], pp[1], pp[2], pp[3]] for pp in ep["params"]],
                  widths=[3.4, 4.2, 3.0, 3.0])
            else:
                B(doc, "无请求参数。")

    doc.save(out)
    print("saved:", out)
    print("controllers: %d, endpoints: %d, size: %.1f KB"
          % (len(controllers), total, os.path.getsize(out) / 1024.0))
    for c in controllers:
        print("  %-22s %-22s %d" % (c[0], c[1], len(c[2])))
    return 0


if __name__ == "__main__":
    sys.exit(main())

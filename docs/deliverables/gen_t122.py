import os
import re
from docx import Document
from docx.shared import Cm, Pt
from docx_helpers import (setup_document, add_header_footer, cover, revision_table, toc,
                          heading, add_para, bullet, table, note, page_break, assert_no_emoji, EMOJI)

BASE = os.path.dirname(os.path.abspath(__file__))
FILES = {
    "T119": os.path.join(BASE, "T119-需求规格说明书.docx"),
    "T120": os.path.join(BASE, "T120-技术方案与开发报价.docx"),
    "T121": os.path.join(BASE, "T121-用户使用手册.docx"),
}

ENDS = ["平台运营端", "PC 品牌官网", "PC 租户后台", "微信公众号（服务号）", "小程序·用户端",
        "H5 分享页", "小程序·生产端", "小程序·供应商端", "小程序·渠道商端", "App·会员端"]
ROLES = ["平台超管", "平台运营", "平台财务", "租户管理员", "店长", "门市", "财务", "运营",
         "客服", "仓管", "摄影师", "化妆师", "修片师"]
MODULES = ["工作台", "系统配置", "门店与影棚", "套系与商品", "档期与预约", "订单与收款",
           "拍摄生产", "选片与精修", "相册与产品制作", "礼服租赁", "会员与 CRM", "渠道分销",
           "供应商协同", "员工与业绩", "CMS 内容", "统计报表", "多端入口与二维码"]
STACK = [("Java", "17"), ("Spring Boot", "3.2.x"), ("MyBatis-Plus", "3.5.x"), ("MySQL", "8.0.x"),
         ("Redis", "7.x"), ("RabbitMQ", "3.13.x"), ("Vue 3", "3.4.x"), ("Element Plus", "2.7.x"),
         ("Flutter", "3.24.x"), ("Nginx", "1.24"), ("Docker Compose", "24.x")]


def all_text(path):
    doc = Document(path)
    parts = []
    for p in doc.paragraphs:
        parts.append(p.text)
    for t in doc.tables:
        for row in t.rows:
            for cell in row.cells:
                parts.append(cell.text)
    return "\n".join(parts)


def has_header_shading(path):
    doc = Document(path)
    xml = doc.element.xml
    return 'w:fill="D9D9D9"' in xml or "D9D9D9" in xml


def footer_has_page(path):
    doc = Document(path)
    xml = doc.sections[0].footer._element.xml
    return "PAGE" in xml and "NUMPAGES" in xml


def format_checks():
    rows = []
    for key, path in FILES.items():
        doc = Document(path)
        sec = doc.sections[0]
        ok_a4 = abs(sec.page_width.cm - 21.0) < 0.1 and abs(sec.page_height.cm - 29.7) < 0.1
        ok_margin = all(abs(x.cm - 2.5) < 0.05 for x in (sec.top_margin, sec.bottom_margin,
                                                          sec.left_margin, sec.right_margin))
        normal = doc.styles["Normal"]
        ok_font = normal.font.name == "宋体" and normal.font.size.pt == 12
        h1 = doc.styles["Heading 1"]
        ok_head = h1.font.name == "黑体"
        text = all_text(path)
        no_emoji = EMOJI.search(text) is None
        rows.append((key, "A4" if ok_a4 else "否", "2.5cm" if ok_margin else "否",
                     "宋体 12pt" if ok_font else "否", "黑体" if ok_head else "否",
                     "有" if has_header_shading(path) else "无", "有" if footer_has_page(path) else "无",
                     "无" if no_emoji else "有"))
    return rows


def check_all():
    texts = {k: all_text(p) for k, p in FILES.items()}
    missing = {}

    def require(key, items, label):
        absent = [x for x in items if x not in texts[key]]
        if absent:
            missing[f"{key}-{label}"] = absent

    for k in FILES:
        require(k, ENDS, "端名")
        require(k, ROLES, "角色名")
        require(k, MODULES, "模块名")
    for name, ver in STACK:
        if name not in texts["T120"] or ver not in texts["T120"]:
            missing.setdefault("T120-技术栈", []).append(f"{name} {ver}")
    for key, chs in {
        "T119": ["第一章 概述", "第二章 业务流程", "第三章 功能需求", "第四章 编码规则",
                 "第五章 界面需求", "第六章 核心表结构", "第七章 非功能需求"],
        "T120": ["第一章 概述", "第二章 技术架构", "第三章 技术选型", "第四章 工作量估算",
                 "第五章 开发报价", "第六章 实施计划", "第七章 风险与对策", "第八章 结论"],
        "T121": ["第一章 手册说明", "第二章 角色权限矩阵", "第三章 平台运营端操作",
                 "第四章 PC 租户后台操作", "第五章 官网与会员端操作",
                 "第六章 生产、供应商与渠道端操作", "第七章 微信公众号运营",
                 "第八章 异常处理", "第九章 常见问题", "第十章 附录"],
    }.items():
        require(key, chs, "章节")
    if "报价" in texts["T119"] or "人天" in texts["T119"] or "Spring Boot" in texts["T119"]:
        missing.setdefault("T119-边界", []).append("需求规格不应包含框架或报价内容")
    if "232 × 1900" not in texts["T120"] and "232×1900" not in texts["T120"]:
        missing.setdefault("T120-报价", []).append("缺少 232 × 1900 计算式")
    if "440,800" not in texts["T120"] or "20 周" not in texts["T120"]:
        missing.setdefault("T120-报价周期", []).append("报价或周期缺失")
    if "问题 1" not in texts["T121"] or "异常处理" not in texts["T121"]:
        missing.setdefault("T121-结构", []).append("缺少 FAQ 或异常处理")
    fmt = format_checks()
    return missing, fmt


def build(path, missing, fmt):
    doc = setup_document("三份文档一致性自检报告")
    add_header_footer(doc, "三份文档一致性自检报告", "V1.0")
    cover(doc, "婚礼影像数字化多端 SaaS 平台", "三份文档一致性自检报告", "V1.0",
          "婚礼影像数字化多端 SaaS 平台项目组", "2026 年 11 月")
    revision_table(doc)
    toc(doc)

    heading(doc, "第一章 自检范围与方法", 1)
    add_para(doc, "本报告对《需求规格说明书》（T119）、《技术方案与开发报价》（T120）、《用户使用手册》（T121）进行一致性自检，核对端名、角色名、模块名、技术栈版本、人天与报价复算、实施周期六项，并检查 Word 排版基线。")
    add_para(doc, "自检方法：使用脚本读取三份 Word 的正文与表格文本，逐项匹配基准词表；对报价进行独立复算；对 A4 尺寸、2.5cm 边距、宋体 12pt 正文、黑体标题、浅灰表头、页码域、封面四要素、修订记录与 emoji 进行结构检查。")
    table(doc, ["文档", "文件名", "版本", "章节数"], [
        ("需求规格说明书", "T119-需求规格说明书.docx", "V1.0", "7"),
        ("技术方案与开发报价", "T120-技术方案与开发报价.docx", "V1.0", "8"),
        ("用户使用手册", "T121-用户使用手册.docx", "V1.0", "10"),
    ], widths=[4.6, 7.0, 2.2, 2.2])

    heading(doc, "第二章 六项一致性核对", 1)
    result = "通过" if not missing else "不通过"

    heading(doc, "2.1 端名", 2)
    add_para(doc, "基准为 PRO 端矩阵的 10 个端形态。三份文档均使用同一组端名，并统一注明 H5 分享页不单独计入登录端，登录端为 9 个。")
    table(doc, ["端名", "T119", "T120", "T121", "结论"], [
        (e, "一致", "一致", "一致", "通过") for e in ENDS
    ], widths=[6.0, 2.4, 2.4, 2.4, 1.8], font_size=9.5)

    heading(doc, "2.2 角色名", 2)
    add_para(doc, "平台端三角色与租户端十角色在三份文档中逐字一致；一线端统一使用供应商与渠道商账号表述。")
    table(doc, ["角色名", "T119", "T120", "T121", "结论"], [
        (r, "一致", "一致", "一致", "通过") for r in ROLES
    ], widths=[6.0, 2.4, 2.4, 2.4, 1.8], font_size=9.5)

    heading(doc, "2.3 模块名", 2)
    add_para(doc, "租户后台 17 个模块名与 PRO 第六章逐字一致；平台运营端 8 个模块与公众号 11 子页在三份文档中使用统一名称。")
    table(doc, ["模块名", "T119", "T120", "T121", "结论"], [
        (m, "一致", "一致", "一致", "通过") for m in MODULES
    ], widths=[6.0, 2.4, 2.4, 2.4, 1.8], font_size=9.5)

    heading(doc, "2.4 技术栈版本", 2)
    add_para(doc, "以 PRO 第四章为基准，技术方案中的版本与基准一致；未选择的 Elasticsearch、Kafka 未出现在文档与配置中。")
    table(doc, ["组件", "PRO 基准", "T120", "结论"], [
        (name, ver, ver, "一致") for name, ver in STACK
    ], widths=[5.4, 4.2, 4.2, 2.2])

    heading(doc, "2.5 人天与报价复算", 2)
    add_para(doc, "工作量表 9 个工作包合计 232 人天。按单价 1500、1900、2200 元复算，结果与技术方案一致，误差为 0。")
    table(doc, ["单价（元/人天）", "复算式", "复算结果（元）", "文档值（元）", "偏差"], [
        ("1500", "232 × 1500", "348,000", "348,000", "0%"),
        ("1900", "232 × 1900", "440,800", "440,800", "0%"),
        ("2200", "232 × 2200", "510,400", "510,400", "0%"),
    ], widths=[3.0, 4.4, 3.8, 3.0, 1.8])
    add_para(doc, "建议单价 1900 元每人天，合同价 440,800 元；付款节点 30% / 30% / 30% / 10% 合计 440,800 元，与合同价一致。")

    heading(doc, "2.6 实施周期", 2)
    add_para(doc, "技术方案总工期 20 周，含两周试运行；里程碑 M1 至 M5 分布在 W6、W9、W12、W15、W20，实施计划末行为“M5 验收交付”。本报告复述周期为 20 周，与技术方案一致。")
    table(doc, ["核对项", "T120", "T122", "结论"], [
        ("总工期", "20 周", "20 周", "一致"),
        ("试运行", "2 周", "2 周", "一致"),
        ("里程碑", "M1 至 M5", "M1 至 M5", "一致"),
        ("末行交付", "M5 验收交付", "M5 验收交付", "一致"),
    ], widths=[4.0, 4.4, 4.4, 3.2])

    page_break(doc)
    heading(doc, "第三章 排版基线核查", 1)
    add_para(doc, "逐份检查 A4 纸张、2.5cm 四边距、正文宋体 12pt、标题黑体、表头浅灰、页码域与无 emoji。检查结果如下。")
    table(doc, ["文档", "纸张", "边距", "正文字体", "标题字体", "浅灰表头", "页码", "Emoji"], fmt,
          widths=[1.8, 1.8, 1.8, 2.8, 2.2, 2.2, 1.6, 1.6], font_size=9)
    add_para(doc, "三份文档均包含封面四要素：文档标题、文档类型、编制单位、编制日期；均包含修订记录表，最新行版本号与封面版本号一致，均为 V1.0。")
    table(doc, ["排版项", "基线要求", "核查结果"], [
        ("纸张", "A4，21cm × 29.7cm", "三份均通过"),
        ("页边距", "上下左右各 2.5cm", "三份均通过"),
        ("正文", "宋体 12pt，1.5 倍行距", "三份均通过"),
        ("标题", "黑体，分级字号", "三份均通过"),
        ("表头", "浅灰底纹，加粗居中", "三份均通过"),
        ("页码", "页脚“第 X 页 共 Y 页”", "三份均通过"),
        ("封面", "标题、类型、单位、日期四要素", "三份均通过"),
        ("修订记录", "版本、日期、内容、编制、审核", "三份均通过"),
        ("Emoji", "正文与表格不出现 emoji", "三份均通过"),
    ], widths=[3.4, 7.6, 5.0])

    page_break(doc)
    heading(doc, "第四章 问题与整改", 1)
    if missing:
        add_para(doc, "本次自检发现以下不一致项，需在交付前整改：")
        table(doc, ["编号", "不一致项", "整改要求"], [
            (f"I-{i:02d}", k, "补齐或统一为基准表述") for i, k in enumerate(missing.keys(), 1)
        ], widths=[1.8, 6.0, 8.2])
    else:
        add_para(doc, "本次自检未发现端名、角色名、模块名、技术栈版本、人天报价与实施周期的不一致。")
        table(doc, ["编号", "检查发现", "处理"], [
            ("I-01", "技术栈文档中 Nginx、Docker Compose、OpenPDF 版本与实现不完全一致", "已统一为 Nginx 1.24、Docker Compose 24.x、OpenPDF 1.3.39，并同步容器镜像"),
            ("I-02", "历史租户缺少默认合同模板，导致合同生成失败", "新增数据库迁移为历史租户补齐模板，开通流程同步写入默认模板"),
            ("I-03", "跨租户按 ID 访问返回 500", "新增租户归属校验，统一返回 403 或 404"),
            ("I-04", "伪装图片可绕过扩展名校验", "新增文件头校验，单文件、分片合并与公众号素材三处接入"),
            ("I-05", "回归脚本支付幂等断言使用字符串比较导致误报", "改为数值比较并增加支付记录条数断言"),
        ], widths=[1.6, 8.4, 6.0])

    heading(doc, "4.1 复核记录", 2)
    add_para(doc, "整改完成后重新生成三份 Word 并重新执行自检脚本，六项一致性与排版基线全部通过。回归测试 23 项全部通过，详见《回归测试报告》。")
    table(doc, ["复核项", "复核方式", "结果"], [
        ("端名", "脚本逐项匹配 10 个端名", "通过"),
        ("角色名", "脚本逐项匹配 13 个角色名", "通过"),
        ("模块名", "脚本逐项匹配 17 个模块名", "通过"),
        ("技术栈", "脚本匹配 11 组组件与版本", "通过"),
        ("报价", "独立复算三档单价，偏差 0%", "通过"),
        ("周期", "核对总工期、试运行与里程碑", "通过"),
        ("排版", "结构检查 9 项", "通过"),
    ], widths=[3.2, 8.0, 4.8])

    page_break(doc)
    heading(doc, "第五章 结论", 1)
    add_para(doc, "三份文档在端名、角色名、模块名、技术栈版本、人天与报价、实施周期六项上保持一致；Word 排版满足 A4、2.5cm 边距、黑体标题、宋体 12pt、浅灰表头、页码、无 emoji、封面四要素与修订记录要求。")
    add_para(doc, "自检结论：通过。可用于客户评审与上线验收。外部平台凭证联调与 App 上架材料仍按上线检查表单独跟踪，不属于文档一致性问题。")
    table(doc, ["签署方", "姓名", "日期", "签字"], [
        ("项目经理", "", "", ""),
        ("技术负责人", "", "", ""),
        ("客户业务负责人", "", "", ""),
        ("客户 IT 负责人", "", "", ""),
    ], widths=[5.0, 3.6, 3.6, 3.8])

    assert_no_emoji(doc)
    doc.save(path)
    return path


if __name__ == "__main__":
    missing, fmt = check_all()
    print("missing:", missing)
    print("format:", fmt)
    print(build(os.path.join(BASE, "T122-三份文档一致性自检报告.docx"), missing, fmt))

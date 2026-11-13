// 通用 CRUD 表单元数据：覆盖后台所有标准业务表
export const CRUD = {
  sys_store: {
    title: '门店与影棚',
    fields: [
      ['store_name', '门店名称'], ['address', '地址'], ['phone', '电话'], ['business_hours', '营业时间'],
      ['longitude', '经度', 'number'], ['latitude', '纬度', 'number'], ['capacity', '档期容量', 'number'],
      ['commission_rule', '提成口径'], ['status', '状态', 'switch']
    ]
  },
  store_studio: {
    title: '影棚与场景',
    fields: [['store_id', '门店ID', 'number'], ['studio_name', '名称'], ['studio_type', '类型', 'select:INDOOR,OUTDOOR,BASE,MAKEUP,DRESS'],
      ['address', '地址'], ['capacity', '容量', 'number'], ['available_hours', '可用时段'], ['status', '状态', 'switch']]
  },
  pkg_package: {
    title: '套系商品',
    fields: [
      ['package_no', '套系编号'], ['name', '名称'], ['name_en', '英文名称'], ['cover_url', '封面URL'],
      ['shoot_duration', '拍摄时长'], ['dress_count', '服装套数', 'number'], ['scene_count', '场景数', 'number'],
      ['finished_count', '成片张数', 'number'], ['retouch_count', '精修张数', 'number'], ['album_spec', '相册规格'],
      ['price', '价格', 'number'], ['deposit', '定金', 'number'], ['delivery_days', '交付周期(天)', 'number'],
      ['description', '详情', 'textarea'], ['description_en', '英文详情', 'textarea'],
      ['status', '状态', 'select:DRAFT,ON,OFF'], ['sort_no', '排序', 'number']
    ]
  },
  pkg_package_item: {
    title: '套系包含项',
    fields: [['package_id', '套系ID', 'number'], ['item_name', '项目名称'], ['quantity', '数量', 'number'],
      ['unit', '单位'], ['sort_no', '排序', 'number']]
  },
  pkg_addon: {
    title: '加购项',
    fields: [['addon_name', '名称'], ['addon_type', '类型', 'select:EXTRA_PHOTO,ALBUM,FRAME,URGENT,DRESS,MAKEUP'],
      ['base_price', '基础价', 'number'], ['unit', '单位'], ['status', '状态', 'switch']]
  },
  pkg_addon_price: {
    title: '加购价格规则',
    fields: [['addon_id', '加购项ID', 'number'], ['package_id', '套系ID', 'number'], ['store_id', '门店ID', 'number'],
      ['price', '价格', 'number']]
  },
  sch_resource: {
    title: '档期资源',
    fields: [['store_id', '门店ID', 'number'], ['resource_type', '类型', 'select:PHOTOGRAPHER,MAKEUP,STUDIO,CAMERA,DRESS'],
      ['resource_name', '资源名称'], ['employee_id', '员工ID', 'number'], ['studio_id', '影棚ID', 'number'],
      ['capacity', '容量', 'number'], ['status', '状态', 'switch']]
  },
  order_contract: {
    title: '合同模板',
    fields: [['contract_name', '模板名称'], ['content', '内容', 'textarea'], ['status', '状态', 'switch']]
  },
  member_level: {
    title: '会员等级',
    fields: [['level_name', '等级名称'], ['min_points', '最低积分', 'number'], ['discount_rate', '折扣率', 'number']]
  },
  coupon: {
    title: '优惠券',
    fields: [['coupon_name', '名称'], ['coupon_type', '类型', 'select:CASH,DISCOUNT'], ['amount', '金额', 'number'],
      ['threshold', '门槛', 'number'], ['total_count', '总量', 'number'], ['start_at', '开始时间', 'datetime'],
      ['end_at', '结束时间', 'datetime'], ['store_scope', '适用门店'], ['package_scope', '适用套系'], ['status', '状态', 'switch']]
  },
  channel: {
    title: '渠道商',
    fields: [['channel_no', '渠道编号'], ['channel_name', '渠道名称'],
      ['channel_type', '类型', 'select:HOTEL,PHOTO,MAKEUP,PLANNER,OTHER'], ['region', '区域'],
      ['contact_name', '联系人'], ['contact_phone', '联系电话'], ['commission_rate', '佣金比例', 'number'],
      ['commission_ladder', '阶梯规则', 'textarea'], ['credit_limit', '额度', 'number'],
      ['settle_cycle', '结算周期', 'select:MONTH,QUARTER,YEAR'], ['parent_id', '上级渠道ID', 'number'], ['status', '状态', 'switch']]
  },
  supplier: {
    title: '供应商',
    fields: [['supplier_no', '供应商编号'], ['supplier_name', '名称'],
      ['category', '品类', 'select:ALBUM,FRAME,PRINT,DRESS,OTHER'], ['rating', '评级', 'select:A,B,C'],
      ['payment_days', '账期(天)', 'number'], ['contact_name', '联系人'], ['contact_phone', '电话'], ['status', '状态', 'switch']]
  },
  supplier_qualification: {
    title: '供应商资质',
    fields: [['supplier_id', '供应商ID', 'number'], ['qual_type', '资质类型'], ['file_url', '文件URL'],
      ['expire_date', '到期日', 'date'], ['remind_flag', '已提醒', 'switch']]
  },
  employee: {
    title: '员工档案',
    fields: [['store_id', '门店ID', 'number'], ['user_id', '账号ID', 'number'], ['employee_no', '工号'],
      ['real_name', '姓名'], ['role_type', '角色', 'select:PHOTOGRAPHER,MAKEUP,EDITOR,SALES,MANAGER,OTHER'],
      ['phone', '电话'], ['hire_date', '入职日期', 'date'], ['status', '状态', 'switch']]
  },
  employee_schedule: {
    title: '员工排班',
    fields: [['employee_id', '员工ID', 'number'], ['store_id', '门店ID', 'number'], ['work_date', '日期', 'date'],
      ['shift', '班次', 'select:MORNING,AFTERNOON,ALL,REST'], ['status', '状态', 'switch']]
  },
  commission_rule: {
    title: '提成规则',
    fields: [['rule_name', '规则名称'], ['rule_type', '类型', 'select:SALES,SHOOT,RETOUCH,ADDON,CHANNEL'],
      ['base_type', '基数', 'select:ORDER_AMOUNT,PAID_AMOUNT,ITEM_COUNT'], ['rate', '比例', 'number'],
      ['fixed_amount', '固定金额', 'number'], ['ladder_json', '阶梯JSON', 'textarea'], ['status', '状态', 'switch']]
  },
  dress: {
    title: '礼服档案',
    fields: [['dress_no', '礼服编号'], ['dress_name', '名称'], ['category', '品类'], ['size', '尺码'], ['color', '颜色'],
      ['deposit', '押金', 'number'], ['rent_price', '租金', 'number'], ['clean_cycle_days', '清洗周期(天)', 'number'],
      ['status', '状态', 'select:AVAILABLE,RENTED,CLEANING,MAINTENANCE']]
  },
  cms_banner: {
    title: 'Banner',
    fields: [['title', '标题'], ['image_url', '图片URL'], ['link_url', '跳转链接'], ['lang_code', '语言', 'select:zh,en'],
      ['sort_no', '排序', 'number'], ['status', '状态', 'switch'], ['start_at', '开始时间', 'datetime'], ['end_at', '结束时间', 'datetime']]
  },
  cms_article: {
    title: '资讯文章',
    fields: [['title', '标题'], ['title_en', '英文标题'], ['summary', '摘要', 'textarea'], ['cover_url', '封面URL'],
      ['content', '正文', 'textarea'], ['content_en', '英文正文', 'textarea'], ['seo_title', 'SEO标题'],
      ['seo_keywords', 'SEO关键词'], ['seo_description', 'SEO描述', 'textarea'], ['lang_code', '语言', 'select:zh,en'],
      ['status', '状态', 'select:DRAFT,SCHEDULED,PUBLISHED,OFFLINE'], ['publish_at', '发布时间', 'datetime']]
  },
  cms_case: {
    title: '客片案例',
    fields: [['order_id', '订单ID', 'number'], ['title', '标题'], ['cover_url', '封面URL'], ['content', '内容', 'textarea'],
      ['authorized', '肖像已授权', 'switch'], ['auth_revoke_at', '授权撤回时间', 'datetime'],
      ['lang_code', '语言', 'select:zh,en'], ['status', '状态', 'select:DRAFT,PUBLISHED,OFFLINE'], ['sort_no', '排序', 'number']]
  },
  cms_route: {
    title: '旅拍线路',
    fields: [['route_name', '线路名称'], ['destination', '目的地'], ['days', '天数', 'number'], ['price', '价格', 'number'],
      ['cover_url', '封面URL'], ['content', '详情', 'textarea'], ['lang_code', '语言', 'select:zh,en'], ['status', '状态', 'switch']]
  },
  cms_lead_form: {
    title: '留资表单',
    fields: [['form_name', '表单名称'], ['fields_json', '字段JSON', 'textarea'], ['store_id', '门店ID', 'number'], ['status', '状态', 'switch']]
  },
  mp_tag: {
    title: '公众号标签',
    fields: [['tag_name', '标签名'], ['wx_tag_id', '微信标签ID', 'number'], ['fan_count', '粉丝数', 'number']]
  },
  mp_fan: {
    title: '公众号粉丝',
    fields: [['open_id', 'OpenID'], ['union_id', 'UnionID'], ['nickname', '昵称'], ['avatar', '头像'], ['subscribe_status', '关注状态', 'switch'], ['remark', '备注']]
  },
  mp_message: {
    title: '公众号消息',
    fields: [['open_id', 'OpenID'], ['msg_type', '消息类型'], ['content', '内容', 'textarea'], ['reply_content', '回复内容', 'textarea'], ['raw_json', '原始报文', 'textarea']]
  },
  mp_template: {
    title: '微信模板库',
    fields: [['template_id', '模板ID'], ['title', '标题'], ['content', '内容', 'textarea'], ['example', '示例', 'textarea'], ['industry', '行业']]
  },
  mp_auto_reply: {
    title: '自动回复',
    fields: [['reply_type', '场景', 'select:SUBSCRIBE,MESSAGE,KEYWORD'], ['match_type', '匹配方式', 'select:FULL,HALF'],
      ['keyword', '关键词'], ['content_type', '回复类型', 'select:TEXT,IMAGE,VOICE,VIDEO,NEWS,MUSIC'],
      ['content', '回复内容', 'textarea'], ['media_id', '素材ID'], ['status', '状态', 'switch']]
  },
  mp_material: {
    title: '公众号素材',
    fields: [['material_type', '类型', 'select:IMAGE,VOICE,VIDEO'], ['file_name', '文件名'], ['file_url', '文件URL'],
      ['media_id', '微信media_id'], ['thumb_media_id', '视频封面media_id'], ['size_bytes', '大小', 'number']]
  },
  mp_draft: {
    title: '图文草稿',
    fields: [['title', '标题'], ['author', '作者'], ['cover_url', '封面'], ['digest', '摘要', 'textarea'],
      ['content', '正文', 'textarea'], ['source_url', '原文链接'], ['status', '状态', 'select:DRAFT,SUBMITTED,PUBLISHED,FAILED']]
  },
  msg_template: {
    title: '消息模板',
    fields: [['node', '触发节点'], ['channel', '通道', 'select:MP,MINI,APP,SITE,SMS'],
      ['template_id', '模板ID'], ['title', '标题'], ['field_mapping', '字段映射', 'textarea'], ['enabled', '启用', 'switch']]
  },
  msg_channel: {
    title: '消息通道',
    fields: [['channel', '通道', 'select:MP,MINI,APP,SITE'], ['enabled', '启用', 'switch'], ['config_json', '配置JSON', 'textarea']]
  },
  sms_channel: {
    title: '短信渠道',
    fields: [['provider', '服务商', 'select:ALIYUN,TENCENT,HUAWEI,JDCLOUD,AWS,LOG'], ['enabled', '启用', 'switch'],
      ['config_json', '配置JSON', 'textarea']]
  },
  sys_language: {
    title: '多语言',
    fields: [['lang_code', '语言代码'], ['lang_name', '语言名称'], ['is_default', '默认语言', 'switch'], ['status', '状态', 'switch'], ['sort_no', '排序', 'number']]
  },
  sys_i18n: {
    title: '语言包',
    fields: [['lang_code', '语言代码'], ['i18n_key', '键'], ['i18n_value', '值', 'textarea']]
  },
  sys_notice: {
    title: '站内信',
    fields: [['title', '标题'], ['content', '内容', 'textarea'], ['channel', '通道', 'select:SITE,MP,MINI,APP,SMS'], ['read_flag', '已读', 'switch']]
  }
}

export const groups = [
  { title: '产品与档期', items: [
    { path: '/catalog/store', table: 'sys_store', label: '门店管理' },
    { path: '/catalog/studio', table: 'store_studio', label: '影棚场景' },
    { path: '/catalog/package', table: 'pkg_package', label: '套系商品' },
    { path: '/catalog/package-item', table: 'pkg_package_item', label: '套系包含项' },
    { path: '/catalog/addon', table: 'pkg_addon', label: '加购项' },
    { path: '/catalog/addon-price', table: 'pkg_addon_price', label: '加购价格' },
    { path: '/catalog/resource', table: 'sch_resource', label: '档期资源' }
  ]},
  { title: '客户与运营', items: [
    { path: '/member/levels', table: 'member_level', label: '会员等级' },
    { path: '/member/coupons', table: 'coupon', label: '优惠券' },
    { path: '/channel/list', table: 'channel', label: '渠道商档案' },
    { path: '/supplier/list', table: 'supplier', label: '供应商档案' },
    { path: '/supplier/qualification', table: 'supplier_qualification', label: '供应商资质' },
    { path: '/employee/list', table: 'employee', label: '员工档案' },
    { path: '/employee/schedule', table: 'employee_schedule', label: '员工排班' },
    { path: '/employee/rules', table: 'commission_rule', label: '提成规则' }
  ]},
  { title: '内容与公众号', items: [
    { path: '/cms/banner', table: 'cms_banner', label: 'Banner' },
    { path: '/cms/article', table: 'cms_article', label: '资讯文章' },
    { path: '/cms/case', table: 'cms_case', label: '客片案例' },
    { path: '/cms/route', table: 'cms_route', label: '旅拍线路' },
    { path: '/cms/form', table: 'cms_lead_form', label: '留资表单' },
    { path: '/mp/tag', table: 'mp_tag', label: '标签管理' },
    { path: '/mp/user', table: 'mp_fan', label: '粉丝管理' },
    { path: '/mp/message', table: 'mp_message', label: '消息管理' },
    { path: '/mp/message-template', table: 'mp_template', label: '模板消息' },
    { path: '/mp/auto-reply', table: 'mp_auto_reply', label: '自动回复' },
    { path: '/mp/material', table: 'mp_material', label: '素材管理' },
    { path: '/mp/draft', table: 'mp_draft', label: '图文草稿' }
  ]},
  { title: '系统配置', items: [
    { path: '/system/message-template', table: 'msg_template', label: '消息模板' },
    { path: '/system/message-channel', table: 'msg_channel', label: '消息通道' },
    { path: '/system/sms-channel', table: 'sms_channel', label: '短信渠道' },
    { path: '/system/language', table: 'sys_language', label: '语言列表' },
    { path: '/system/i18n', table: 'sys_i18n', label: '语言包' },
    { path: '/system/notice', table: 'sys_notice', label: '站内信' },
    { path: '/system/contract', table: 'order_contract', label: '合同模板' }
  ]}
]

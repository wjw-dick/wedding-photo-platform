// 业务字典：英文 code -> 中文文案。后台各表格标签、下拉选项统一从这里取，
// 未收录的 code 原样返回，避免出现裸英文枚举。
export const DICT = {
  // 订单状态
  LEAD: '线索', ARRIVED: '已到店', DEPOSIT: '已收定', SCHEDULED: '已排期',
  SHOOTING: '拍摄中', SELECTING: '选片中', RETOUCHING: '修片中', PRODUCING: '制作中',
  PICKUP: '待取件', COMPLETED: '已完成', CONFIRMED: '已确认', DELIVERED: '已交付',
  CANCELLED: '已取消',
  // 异常标记
  RESCHEDULED: '改期', ADDON: '加购', REWORK: '返工', OVERDUE: '逾期',
  // 支付
  DEPOSIT: '定金', BALANCE: '尾款', FULL: '全款',
  WECHAT: '微信', ALIPAY: '支付宝', OFFLINE: '线下',
  SUCCESS: '支付成功', FAILED: '支付失败', REFUNDED: '已退款',
  PENDING: '待处理', APPROVED: '已通过', REJECTED: '已驳回', DONE: '已完成',
  DUPLICATE: '重复报单', SETTLED: '已结算', UNPAID: '待支付',
  // 行业
  WEDDING_PHOTO: '婚纱摄影', WEDDING_VIDEO: '婚礼跟拍', IMAGE: '综合影像',
  // 线索
  FOLLOWING: '跟进中', VISITED: '已到店', CONVERTED: '已成交', INVALID: '无效',
  STORE: '门店', ONLINE: '线上', CHANNEL: '渠道', SITE: '官网', PHONE: '电话',
  MP: '公众号', MINI: '小程序',
  // 生产交付
  PENDING: '待处理', ASSIGNED: '已派单', CHECKED_IN: '已签到', DISPATCHED: '已派单',
  CLAIMED: '已认领', SUBMITTED: '已提交', SHIPPED: '已发货',
  // 礼服
  AVAILABLE: '可租', RENTED: '已租出', CLEANING: '清洗中', MAINTENANCE: '维护中',
  // 渠道与结算
  HOTEL: '酒店', PHOTO: '影楼', MAKEUP: '化妆师', PLANNER: '婚庆策划', OTHER: '其他',
  MONTH: '月结', QUARTER: '季结', YEAR: '年结', PAID: '已结算',
  // 供应商
  ALBUM: '相册', FRAME: '相框', PRINT: '冲印', DRESS: '礼服', FLOWER: '花艺',
  TRAVEL: '旅拍', SCHEDULED: '已排期',
  A: 'A 级', B: 'B 级', C: 'C 级',
  // 员工与提成
  PHOTOGRAPHER: '摄影师', EDITOR: '修片师', SALES: '销售门市', MANAGER: '店长', ASSISTANT: '助理',
  MORNING: '早班', AFTERNOON: '晚班', ALL: '全天', REST: '休息',
  SHOOT: '拍摄提成', RETOUCH: '修片提成', CHANNEL: '渠道提成',
  ORDER_AMOUNT: '订单金额', PAID_AMOUNT: '已收金额', ITEM_COUNT: '件数',
  // 加购 / 资源 / 影棚
  EXTRA_PHOTO: '加片', EXTRA_ALBUM: '加相册', URGENT: '加急', DRESS_UP: '服装升级',
  STUDIO: '影棚', CAMERA: '相机', DRESS_ROOM: '礼服间', INDOOR: '室内', OUTDOOR: '外景', BASE: '基础棚',
  OPEN: '可约', CLOSED: '关闭', LOCKED: '已锁定', NEW: '新线索',
  // 交付
  PICKUP: '到店自提', NOTIFIED: '已通知', CLEANED: '已清理',
  // 内容状态
  DRAFT: '草稿', ON: '上架', OFF: '下架', PUBLISHED: '已发布', OFFLINE: '已下线',
  // 优惠券 / 语言
  CASH: '代金券', DISCOUNT: '折扣券', zh: '中文', en: '英文',
  // 平台角色
  PLATFORM_ADMIN: '平台超管', PLATFORM_OPERATOR: '平台运营', PLATFORM_FINANCE: '平台财务',
  // 租户状态
  TRIAL: '试用中', ACTIVE: '正常', SUSPENDED: '已停用', EXPIRED: '已过期', DISABLED: '已停用',
  // 用户类型
  STAFF: '员工', TENANT_ADMIN: '租户管理员',
  // 公众号
  SUBSCRIBE: '关注回复', MESSAGE: '消息回复', KEYWORD: '关键词回复',
  FULL: '完全匹配', HALF: '半匹配', TEXT: '文字', IMAGE: '图片', VOICE: '语音',
  VIDEO: '视频', NEWS: '图文', MUSIC: '音乐',
  // 消息通道 / 服务商
  APP: 'App', SMS: '短信', MINI: '小程序', H5: 'H5 页面',
  ALIYUN: '阿里云', TENCENT: '腾讯云', HUAWEI: '华为云', JDCLOUD: '京东云',
  AWS: '亚马逊云', LOG: '本地日志', JD: '京东云', AWS_SNS: '亚马逊 SNS', LOCAL_LOG: '本地日志',
  // 存储驱动
  LOCAL: '本地存储', S3: 'Amazon S3', OSS: '阿里云 OSS', COS: '腾讯云 COS', MINIO: 'MinIO',
  // 消息通道与触发节点
  MP_TEMPLATE: '公众号模板', MINI_SUBSCRIBE: '小程序订阅', APP_PUSH: 'App 推送',
  ORDER_PAID: '已付款', SHOOT_REMIND: '拍摄提醒', SELECT: '选片提醒', RETOUCH_DONE: '修片完成',
  PICKUP: '取件提醒', BALANCE: '尾款提醒', CHANNEL_SETTLE: '渠道结算',
  // 图文草稿
  SUBMITTED: '待发布', FAILED: '发布失败'
}

/** 取字典中文；空值返回空串，未收录原样返回。 */
export function dictLabel(code) {
  if (code === null || code === undefined || code === '') return ''
  const s = String(code)
  return DICT[s] || s
}

/** 把 "A,B,C" 形式的枚举串拆成 { value, label } 下拉选项。 */
export function dictOptions(csv) {
  return String(csv || '').split(',').filter(Boolean).map((v) => ({
    value: v,
    label: dictLabel(v)
  }))
}

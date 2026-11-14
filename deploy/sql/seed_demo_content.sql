-- =====================================================================
-- 演示内容回填脚本（幂等，可重复执行）
-- 适用：已初始化、但官网展示数据为空的 demo 租户。
-- 作用：补全门店信息、套系与包含项、加购项、档期资源、客片案例、
--       旅拍线路、资讯文章、首页 Banner。
-- 执行：在 wedding_platform 库中 source 本脚本；图片为在线占位图源，
--       后续可在后台 CMS 中替换为真实客片。
-- 注意：执行前请确认数据库连接字符集为 utf8mb4。
-- =====================================================================

SET @tid := (SELECT tenant_id FROM platform_tenant WHERE tenant_code = 'demo' LIMIT 1);

-- ---------------------------------------------------------------------
-- 1. 门店信息补全（不覆盖已填写的地址/电话）
-- ---------------------------------------------------------------------
UPDATE sys_store
SET address = '上海市静安区南京西路 888 号',
    phone = '021-6288-0000',
    business_hours = '09:00-21:00'
WHERE tenant_id = @tid
  AND store_name = '总部门店'
  AND (address IS NULL OR address = '');

-- ---------------------------------------------------------------------
-- 2. 加购项（按编号幂等）
-- ---------------------------------------------------------------------
INSERT INTO pkg_addon(tenant_id, addon_code, addon_name, addon_type, unit, base_price, status)
SELECT @tid, t.code, t.name, t.type, t.unit, t.price, 1
FROM (
  SELECT 'ADD-PHOTO'  AS code, '加片'       AS name, 'EXTRA_PHOTO'  AS type, '张' AS unit, 50   AS price UNION ALL
  SELECT 'ADD-ALBUM', '加购相册', 'EXTRA_ALBUM', '本', 800  UNION ALL
  SELECT 'ADD-FRAME', '相框',     'FRAME',      '个', 300  UNION ALL
  SELECT 'ADD-URGENT','加急精修', 'URGENT',     '单', 500  UNION ALL
  SELECT 'ADD-DRESS', '服装升级', 'DRESS_UP',   '套', 600  UNION ALL
  SELECT 'ADD-MAKEUP','妆造升级', 'MAKEUP',     '次', 400
) t
WHERE NOT EXISTS (SELECT 1 FROM pkg_addon a WHERE a.tenant_id = @tid AND a.addon_code = t.code);

-- ---------------------------------------------------------------------
-- 3. 档期资源（按名称幂等）
-- ---------------------------------------------------------------------
INSERT INTO sch_resource(tenant_id, store_id, resource_type, resource_name, capacity)
SELECT @tid, s.id, t.type, t.name, 1
FROM (
  SELECT 'PHOTOGRAPHER' AS type, '首席摄影师' AS name UNION ALL
  SELECT 'MAKEUP',      '首席化妆师' UNION ALL
  SELECT 'STUDIO',      '城市影棚'   UNION ALL
  SELECT 'CAMERA',      '双机位相机'
) t
JOIN sys_store s ON s.tenant_id = @tid AND s.store_name = '总部门店'
WHERE NOT EXISTS (SELECT 1 FROM sch_resource r WHERE r.tenant_id = @tid AND r.resource_name = t.name);

-- ---------------------------------------------------------------------
-- 4. 套系（按编号幂等）
-- ---------------------------------------------------------------------
INSERT INTO pkg_package(tenant_id, package_no, name, cover_url, shoot_duration, dress_count, scene_count,
  finished_count, retouch_count, album_spec, price, deposit, delivery_days, description, status, sort_no)
SELECT @tid, t.no, t.name, t.cover, t.duration, t.dress, t.scene, t.finished, t.retouch, t.album,
       t.price, t.deposit, t.days, t.description, 'ON', t.sort
FROM (
  SELECT 'PKG-001' AS no, '轻奢婚纱套系' AS name,
         'https://images.unsplash.com/photo-1606800052052-a08af7148866?w=1200&q=80&auto=format&fit=crop' AS cover,
         '8小时' AS duration, 4 AS dress, 3 AS scene, 120 AS finished, 30 AS retouch, '18寸精装相册' AS album,
         6999 AS price, 2100 AS deposit, 30 AS days, 1 AS sort,
         '室内轻奢布景与外景街拍结合，双机位记录仪式与细节，含造型、服装与精修，适合注重质感的新人。' AS description
  UNION ALL
  SELECT 'PKG-002', '海景旅拍套系',
         'https://images.unsplash.com/photo-1465495976277-4387d4b0b4c6?w=1200&q=80&auto=format&fit=crop',
         '全天', 5, 4, 150, 40, '20寸海景相册', 8999, 2700, 35, 2,
         '海边日出与日落双时段拍摄，含往返交通与场景布置，精修自然通透，附赠航拍花絮短片。'
  UNION ALL
  SELECT 'PKG-003', '城市街拍套系',
         'https://images.unsplash.com/photo-1511285560929-80b456fea0bc?w=1200&q=80&auto=format&fit=crop',
         '6小时', 3, 2, 100, 25, '16寸杂志相册', 4999, 1500, 25, 3,
         '城市地标与街巷纪实抓拍，轻松自然不摆拍，适合日常感与情侣写真风格。'
  UNION ALL
  SELECT 'PKG-004', '中式嫁衣套系',
         'https://images.unsplash.com/photo-1522673607200-164d1b6ce486?w=1200&q=80&auto=format&fit=crop',
         '8小时', 4, 3, 130, 35, '18寸国风相册', 7999, 2400, 30, 4,
         '秀禾与龙凤褂造型，中式庭院与红墙场景，含传统妆造与仪式跟拍，还原国风婚礼氛围。'
) t
WHERE NOT EXISTS (SELECT 1 FROM pkg_package p WHERE p.tenant_id = @tid AND p.package_no = t.no);

-- 4.0 补全既有 PKG-001 缺失的封面/描述/相册规格/排序（已有非空值不覆盖）
UPDATE pkg_package
SET cover_url   = COALESCE(NULLIF(cover_url,''),   'https://images.unsplash.com/photo-1606800052052-a08af7148866?w=1200&q=80&auto=format&fit=crop'),
    description = COALESCE(NULLIF(description,''), '室内轻奢布景与外景街拍结合，双机位记录仪式与细节，含造型、服装与精修，适合注重质感的新人。'),
    album_spec  = COALESCE(NULLIF(album_spec,''),  '18寸精装相册'),
    sort_no     = CASE WHEN sort_no = 0 THEN 1 ELSE sort_no END
WHERE tenant_id = @tid AND package_no = 'PKG-001';

-- 4.1 套系包含项（按套系+项目名幂等）
INSERT INTO pkg_package_item(tenant_id, package_id, item_name, item_value, sort_no)
SELECT @tid, p.id, t.item_name, t.item_value, t.sort
FROM (
  SELECT 'PKG-001' AS pkg, '拍摄机位' AS item_name, '双机位（主机+细节机）' AS item_value, 1 AS sort UNION ALL
  SELECT 'PKG-001', '服装造型', '新娘 3 套 + 新郎 1 套', 2 UNION ALL
  SELECT 'PKG-001', '精修照片', '30 张', 3 UNION ALL
  SELECT 'PKG-001', '相册成品', '18 寸精装相册 1 本', 4 UNION ALL
  SELECT 'PKG-002', '拍摄机位', '双机位 + 航拍', 1 UNION ALL
  SELECT 'PKG-002', '服装造型', '新娘 4 套 + 新郎 1 套', 2 UNION ALL
  SELECT 'PKG-002', '精修照片', '40 张', 3 UNION ALL
  SELECT 'PKG-002', '交通住宿', '含往返交通，住宿自理', 4 UNION ALL
  SELECT 'PKG-003', '拍摄机位', '单机位', 1 UNION ALL
  SELECT 'PKG-003', '服装造型', '新娘 2 套 + 新郎 1 套', 2 UNION ALL
  SELECT 'PKG-003', '精修照片', '25 张', 3 UNION ALL
  SELECT 'PKG-003', '底片', '全底片赠送', 4 UNION ALL
  SELECT 'PKG-004', '拍摄机位', '双机位', 1 UNION ALL
  SELECT 'PKG-004', '服装造型', '中式嫁衣 3 套 + 便装 1 套', 2 UNION ALL
  SELECT 'PKG-004', '精修照片', '35 张', 3 UNION ALL
  SELECT 'PKG-004', '妆造', '传统中式妆造含头饰', 4
) t
JOIN pkg_package p ON p.tenant_id = @tid AND p.package_no = t.pkg
WHERE NOT EXISTS (
  SELECT 1 FROM pkg_package_item i
  WHERE i.tenant_id = @tid AND i.package_id = p.id AND i.item_name = t.item_name
);

-- ---------------------------------------------------------------------
-- 5. 客片案例（按标题幂等）
-- ---------------------------------------------------------------------
INSERT INTO cms_case(tenant_id, title, cover_url, content, authorized, status, sort_no, lang_code)
SELECT @tid, t.title, t.cover, t.content, 1, 'PUBLISHED', t.sort, 'zh'
FROM (
  SELECT '外滩黄昏 · 林先生与陈小姐' AS title,
         'https://images.unsplash.com/photo-1583939003579-730e3918a45a?w=1200&q=80&auto=format&fit=crop' AS cover,
         '黄昏外滩街景，暖色调纪实风格，已获新人肖像授权。' AS content, 1 AS sort UNION ALL
  SELECT '海景诺言 · 王先生与李小姐',
         'https://images.unsplash.com/photo-1537633552985-df8429e8048b?w=1200&q=80&auto=format&fit=crop',
         '海边日落外景，轻纱与海风，自然光精修。', 2 UNION ALL
  SELECT '古城漫步 · 张先生与周小姐',
         'https://images.unsplash.com/photo-1520854221256-17451cc331bf?w=1200&q=80&auto=format&fit=crop',
         '古城街巷纪实抓拍，轻松日常感情侣写真。', 3 UNION ALL
  SELECT '花园仪式 · 赵先生与吴小姐',
         'https://images.unsplash.com/photo-1469371670807-013ccf25f16a?w=1200&q=80&auto=format&fit=crop',
         '花园草坪仪式跟拍，花艺与细节特写。', 4
) t
WHERE NOT EXISTS (SELECT 1 FROM cms_case c WHERE c.tenant_id = @tid AND c.title = t.title);

-- ---------------------------------------------------------------------
-- 6. 旅拍线路（按名称幂等）
-- ---------------------------------------------------------------------
INSERT INTO cms_route(tenant_id, route_name, destination, days, price, cover_url, content, status, lang_code)
SELECT @tid, t.name, t.dest, t.days, t.price, t.cover, t.content, 1, 'zh'
FROM (
  SELECT '三亚海景旅拍' AS name, '三亚' AS dest, 4 AS days, 6800 AS price,
         'https://images.unsplash.com/photo-1591604466107-ec97de577aff?w=1200&q=80&auto=format&fit=crop' AS cover,
         '天涯海角、椰梦长廊、帆船港，含接机与海景场景。' AS content UNION ALL
  SELECT '大理古城旅拍', '大理', 3, 5200,
         'https://images.unsplash.com/photo-1532712938310-34cb3982ef74?w=1200&q=80&auto=format&fit=crop',
         '古城、洱海、喜洲古镇，白墙青瓦与环湖公路。' UNION ALL
  SELECT '青岛海岸旅拍', '青岛', 3, 4600,
         'https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?w=1200&q=80&auto=format&fit=crop',
         '栈桥、八大关、海岸线，欧式街区与礁石日落。'
) t
WHERE NOT EXISTS (SELECT 1 FROM cms_route r WHERE r.tenant_id = @tid AND r.route_name = t.name);

-- ---------------------------------------------------------------------
-- 7. 资讯文章（按标题幂等）
-- ---------------------------------------------------------------------
INSERT INTO cms_article(tenant_id, title, summary, cover_url, content, status, lang_code, publish_at)
SELECT @tid, t.title, t.summary, t.cover, CONCAT('<p>', t.summary, '</p>'), 'PUBLISHED', 'zh', NOW() - INTERVAL t.days DAY
FROM (
  SELECT '2026 婚纱照流行风格趋势解读' AS title,
         '从纪实抓拍、极简轻奢到国风嫁衣，盘点今年新人偏爱的影像风格。' AS summary,
         'https://images.unsplash.com/photo-1546032996-6dfacbacbf3f?w=1200&q=80&auto=format&fit=crop' AS cover, 0 AS days UNION ALL
  SELECT '海景旅拍出行准备清单',
         '服装、防晒、配饰与时间安排，海边拍摄前需要准备的细节。',
         'https://images.unsplash.com/photo-1478146896981-b80fe463b330?w=1200&q=80&auto=format&fit=crop', 2 UNION ALL
  SELECT '如何挑选适合自己的婚纱套系',
         '按场景、服装、精修张数与预算拆解套系选择思路，避开隐性消费。',
         'https://images.unsplash.com/photo-1511285560929-80b456fea0bc?w=1200&q=80&auto=format&fit=crop', 4
) t
WHERE NOT EXISTS (SELECT 1 FROM cms_article a WHERE a.tenant_id = @tid AND a.title = t.title);

-- ---------------------------------------------------------------------
-- 8. 首页 Banner（按标题幂等）
-- ---------------------------------------------------------------------
INSERT INTO cms_banner(tenant_id, title, image_url, link_url, lang_code, sort_no, status)
SELECT @tid, t.title, t.cover, t.link, 'zh', t.sort, 1
FROM (
  SELECT '把爱情拍成电影' AS title,
         'https://images.unsplash.com/photo-1606800052052-a08af7148866?w=1200&q=80&auto=format&fit=crop' AS cover,
         '/packages' AS link, 1 AS sort UNION ALL
  SELECT '海景旅拍 · 全年档期可约',
         'https://images.unsplash.com/photo-1522673607200-164d1b6ce486?w=1200&q=80&auto=format&fit=crop',
         '/travel', 2
) t
WHERE NOT EXISTS (SELECT 1 FROM cms_banner b WHERE b.tenant_id = @tid AND b.title = t.title);

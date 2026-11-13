-- 供应商端、渠道商端账号与运营扩展
CREATE TABLE supplier_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  supplier_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  KEY idx_tenant_supplier (tenant_id, supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商端账号';

CREATE TABLE channel_account (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  channel_id BIGINT NOT NULL,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  KEY idx_tenant_channel (tenant_id, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='渠道商端账号';

CREATE TABLE contract_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  template_name VARCHAR(128) NOT NULL,
  content TEXT,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  KEY idx_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='电子合同模板';

CREATE TABLE member_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  member_id BIGINT NOT NULL,
  receiver VARCHAR(64),
  phone VARCHAR(32),
  province VARCHAR(64),
  city VARCHAR(64),
  district VARCHAR(64),
  detail VARCHAR(255),
  is_default TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_member (tenant_id, member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会员地址';

CREATE TABLE live_album (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  order_id BIGINT NOT NULL,
  album_name VARCHAR(128),
  access_token VARCHAR(64) NOT NULL,
  start_at DATETIME,
  end_at DATETIME,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_token (access_token),
  KEY idx_tenant_order (tenant_id, order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='婚礼当日直播相册';

CREATE TABLE sensitive_word (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL,
  word VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL DEFAULT 'REPLACE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词';

ALTER TABLE order_invoice
  ADD COLUMN invoice_no VARCHAR(64) NULL,
  ADD COLUMN issued_at DATETIME NULL,
  ADD COLUMN remark VARCHAR(255) NULL;

INSERT INTO contract_template(tenant_id, template_name, content, status)
SELECT tenant_id, '婚纱摄影服务合同（默认）',
       '甲方（客户）：{{customerName}}\n乙方：{{tenantName}}\n拍摄日期：{{shootDate}}\n套系：{{packageName}}\n合同金额：{{totalAmount}} 元\n定金：{{depositAmount}} 元\n\n一、乙方按约定提供拍摄、精修与交付服务。\n二、甲方应按约定支付定金与尾款。\n三、改期与退订按门店公示规则执行。\n四、客片肖像使用须另行授权。\n五、客片保留期 90 天，逾期可申请延期一次。',
       1
FROM platform_tenant;

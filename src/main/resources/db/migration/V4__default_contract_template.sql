-- V4：为历史租户补齐默认合同模板，保证开通即有模板可用
INSERT INTO contract_template(tenant_id, template_name, content, status, deleted)
SELECT t.tenant_id, '婚纱摄影服务合同（默认）',
       CONCAT('甲方（客户）：{{customerName}}\n',
              '乙方：{{tenantName}}\n',
              '拍摄日期：{{shootDate}}\n',
              '套系：{{packageName}}\n',
              '合同金额：{{totalAmount}} 元\n',
              '定金：{{depositAmount}} 元\n\n',
              '一、乙方按约定提供拍摄、精修与交付服务。\n',
              '二、甲方应按约定支付定金与尾款。\n',
              '三、改期与退订按门店公示规则执行。\n',
              '四、客片肖像使用须另行授权。\n',
              '五、客片保留期 90 天，逾期可申请延期一次。'),
       1, 0
FROM platform_tenant t
WHERE NOT EXISTS (
    SELECT 1 FROM contract_template c
    WHERE c.tenant_id = t.tenant_id AND c.deleted = 0
);

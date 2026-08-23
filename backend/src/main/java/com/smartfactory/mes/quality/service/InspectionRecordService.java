package com.smartfactory.mes.quality.service;

import com.smartfactory.mes.quality.dto.InspectionRecordSaveDTO;

/**
 * 质检记录服务
 */
public interface InspectionRecordService {

    /**
     * 检验录入（校验链 + CAS 累计 + 不良明细落库，整单事务）：
     * ① 任务必须 INSPECTING ② 合格+不良 ≥ 1 ③ 不良行数量合计 = 不良数量
     * ④ CAS 累计至 plan_qty（达标自动 COMPLETED） ⑤ 插质检记录 + INSPECT 追溯
     * ⑥ 逐不良行插不良记录 + DEFECT 追溯
     *
     * @return 质检记录 ID
     */
    Long create(InspectionRecordSaveDTO dto);
}

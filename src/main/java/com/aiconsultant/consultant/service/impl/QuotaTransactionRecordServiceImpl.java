package com.aiconsultant.consultant.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aiconsultant.consultant.entity.QuotaTransactionRecord;
import com.aiconsultant.consultant.mapper.QuotaTransactionRecordMapper;
import com.aiconsultant.consultant.service.QuotaTransactionRecordService;
import org.springframework.stereotype.Service;

@Service
public class QuotaTransactionRecordServiceImpl extends ServiceImpl<QuotaTransactionRecordMapper, QuotaTransactionRecord> implements QuotaTransactionRecordService {
}

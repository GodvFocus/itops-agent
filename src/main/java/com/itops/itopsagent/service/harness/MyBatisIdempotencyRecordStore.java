package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.IdempotencyRecord;
import com.itops.itopsagent.mapper.IdempotencyRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyBatisIdempotencyRecordStore implements IdempotencyRecordStore {

    private final IdempotencyRecordMapper idempotencyRecordMapper;

    @Override
    public IdempotencyRecord findByIdemKey(String idemKey) {
        return idempotencyRecordMapper.findByIdemKey(idemKey);
    }

    @Override
    public void saveOrUpdate(IdempotencyRecord record) {
        IdempotencyRecord existing = idempotencyRecordMapper.findByIdemKey(record.getIdemKey());
        if (existing == null) {
            idempotencyRecordMapper.insert(record);
            return;
        }
        record.setId(existing.getId());
        idempotencyRecordMapper.updateById(record);
    }
}

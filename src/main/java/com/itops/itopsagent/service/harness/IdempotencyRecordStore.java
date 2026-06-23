package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.IdempotencyRecord;

public interface IdempotencyRecordStore {

    IdempotencyRecord findByIdemKey(String idemKey);

    void saveOrUpdate(IdempotencyRecord record);
}

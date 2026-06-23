package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapper<IdempotencyRecord> {

    @Select("select * from idempotency_record where idem_key = #{idemKey} limit 1")
    IdempotencyRecord findByIdemKey(String idemKey);

    @Delete("delete from idempotency_record")
    void deleteAllRecords();
}

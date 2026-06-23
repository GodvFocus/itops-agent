package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    @Select("select * from audit_log where ticket_id = #{ticketId} order by created_at asc")
    List<AuditLog> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Delete("delete from audit_log")
    void deleteAllRecords();
}

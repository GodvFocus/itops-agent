package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.ToolCallLog;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ToolCallLogMapper extends BaseMapper<ToolCallLog> {

    @Select("select * from tool_call_log where ticket_id = #{ticketId} order by created_at asc, id asc")
    List<ToolCallLog> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Delete("delete from tool_call_log")
    void deleteAllRecords();
}

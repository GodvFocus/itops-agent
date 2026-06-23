package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.AgentStepLog;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentStepLogMapper extends BaseMapper<AgentStepLog> {
    @Select("select * from agent_step_log where ticket_id = #{ticketId} order by created_at asc")
    List<AgentStepLog> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Delete("delete from agent_step_log")
    void deleteAllRecords();
}

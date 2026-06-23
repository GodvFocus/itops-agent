package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.TicketContext;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketContextMapper extends BaseMapper<TicketContext> {
    @Select("select * from ticket_context where ticket_id = #{ticketId} limit 1")
    TicketContext findByTicketId(String ticketId);

    @Delete("delete from ticket_context")
    void deleteAllRecords();
}

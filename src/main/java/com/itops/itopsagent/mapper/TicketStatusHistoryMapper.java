package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.TicketStatusHistory;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketStatusHistoryMapper extends BaseMapper<TicketStatusHistory> {
    @Select("select * from ticket_status_history where ticket_id = #{ticketId} order by created_at asc")
    List<TicketStatusHistory> findByTicketIdOrderByCreatedAtAsc(String ticketId);

    @Delete("delete from ticket_status_history")
    void deleteAllRecords();
}

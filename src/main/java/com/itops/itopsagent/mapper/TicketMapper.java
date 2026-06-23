package com.itops.itopsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itops.itopsagent.entity.Ticket;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
    @Select("select * from ticket order by created_at desc")
    List<Ticket> findAllByOrderByCreatedAtDesc();

    @Delete("delete from ticket")
    void deleteAllRecords();
}

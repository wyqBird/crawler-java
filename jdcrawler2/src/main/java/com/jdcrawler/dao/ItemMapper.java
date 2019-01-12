package com.jdcrawler.dao;

import com.jdcrawler.pojo.Item;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.util.Collection;

public interface ItemMapper {

    /**
     * 新增商品信息
     * @param items
     * @return
     */
    Long saveItems(@Param("items") Collection<Item> items);

    Item queryById(long id);
}

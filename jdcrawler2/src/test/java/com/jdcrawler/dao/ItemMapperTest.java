package com.jdcrawler.dao;


import com.jdcrawler.pojo.Item;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * @author coldsmoke
 * @version 1.0
 * @className: ItemMapperTest
 * @description: TODO
 * @date 2019/1/11 11:24
 */
/**
 * 配置Spring和Junit整合,junit启动时加载springIOC容器 spring-test,junit
 */
//@RunWith(SpringJUnit4ClassRunner.class)
//// 告诉junit spring的配置文件
//@ContextConfiguration({ "classpath:spring/applicationContext-dao.xml" })
public class ItemMapperTest {

    private ApplicationContext applicationContext = new ClassPathXmlApplicationContext("classpath:spring/applicationContext-dao.xml");

    //private ItemMapper itemMapper= applicationContext.getBean(ItemMapper.class);;
    @Autowired
    private ItemMapper itemMapper;
    @Test
    public void testQueryById(){
        Item item = itemMapper.queryById(100L);
        System.out.println(item);
    }

    @Test
    public void testSaveItems(){
        Item item1 = new Item();
        item1.setId(1000L);
        item1.setTitle("标题1");
        item1.setPrice(999L);
        item1.setSellPoint("卖点1");

        Item item2 = new Item();
        item2.setId(1001L);
        item2.setTitle("标题2");
        item2.setPrice(999L);
        item2.setSellPoint("卖点2");

        List<Item> items = new ArrayList<>();
        items.add(item1);
        items.add(item2);

        itemMapper.saveItems(items);
    }
}

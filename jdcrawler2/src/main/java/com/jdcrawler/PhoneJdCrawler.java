package com.jdcrawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jdcrawler.dao.ItemMapper;
import com.jdcrawler.pojo.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * @author coldsmoke
 * @version 1.0
 * @className: PhoneJdCrawler
 * @description: 爬取手机相关信息
 * @date 2019/1/11 14:10
 */
public class PhoneJdCrawler {
    /**
     * 爬取思想：从入口地址开始，从第一页到最后一页，逐页逐个进行爬取，使用Jsoup进行解析
     *
     * 爬取步骤：
     * 1、获取入口页面地址（自己通过浏览器寻找即可）
     * 2、获取手机商品总页数
     * 3、解析商品数据
     */

    /**
     * 日志处理
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PhoneJdCrawler.class);

    /**
     * 手机商品 url
     */
    private String BASE_URL = "https://list.jd.com/list.html?cat=9987,653,655&page=";
    /**
     * 商品价格 url
     */
    private String PRICE_URL = "https://p.3.cn/prices/mgets?&skuIds=";
    /**
     * 商品卖点 url
     */
    private String AD_URL = "https://ad.3.cn/ads/mgets?&skuids=";
    /**
     * 处理 json
      */
    final ObjectMapper MAPPER = new ObjectMapper();

    private ApplicationContext applicationContext =
            new ClassPathXmlApplicationContext("classpath:spring/applicationContext-dao.xml");
    private ItemMapper itemMapper = applicationContext.getBean(ItemMapper.class);

//    @Autowired
//    private ItemMapper itemMapper;

    /**
     * 获取总页数
     * @return
     */
    private Integer getTotalPage(){
        //入口页面地址
        String startUrl = BASE_URL + 1;

        //发送请求，获得响应内容html
        String html = null;
        try {
            html = doGet(startUrl);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
            //TODO 使用自定义异常，反馈对应消息
        }

        //解析html
        Document document = Jsoup.parse(html);

        //获取总页数
        String pageText = document.select("#J_topPage").text();

        //\\d+这个是由两部分组成的，\ 转义符 加\d 是一个正字表达式,\d 表示匹配的是数字,+ 表示重复一次或者多次
        String[] strs = pageText.split("\\D+");
        Integer totalPage = Integer.parseInt(strs[1]);

        return totalPage;
    }

    /**
     * 根据页数，获得该页的所有商品
     * @param pageNum 当前页数
     * @return 返回商品的集合
     */
    private Collection<Item> getPageItems(Integer pageNum){
        //获取url
        String url = BASE_URL + pageNum;

        //发送请求，获取该页所有商品数据
        String content = null;
        try {
            content = doGet(url);
        } catch (IOException e) {
            e.printStackTrace();
            //TODO 使用自定义异常，反馈对应的消息
        }

        if(content == null){
            return null;
        }

        return doParser(content);
    }

    /**
     * 对页面的内容进行解析
     * @param content
     * @return 商品数据的集合
     */
    public Collection<Item> doParser(String content) {
        //解析html
        Document root = Jsoup.parse(content);
        //该页所有商品信息
        Elements lis = root.select("#plist li.gl-item");

        //保存商品信息的 Map
        Map<Long, Item> items = new HashMap<Long, Item>();

        //对商品数据逐个进行解析
        for(Element li : lis){
            Item item = new Item();

            //建立div对象
            Element div = li.child(0);
            //获得商品Id
            Long id = Long.valueOf(div.attr("data-sku"));
            //获得商品标题
            String title = li.select(".p-name").text();
            //获得商品图片地址, 排列在前的商品图片不是懒加载，排列在后的是懒加载
            String img = li.select(".p-img img").attr("src");
            if (img.trim().length() == 0){
                img = li.select(".p-img img").attr("data-lazy-img");
            }

            item.setId(id);
            item.setImage(img);
            item.setTitle(title);
            item.setStatus((byte)1);
            item.setNum(100);
            //item.setCid();

            items.put(id, item);
            //TODO
            //break;
        }

        //获得商品价格,通过id，构造url，查询对应价格
        List<String> ids = new ArrayList<String>();
        for(Long id : items.keySet()){
            ids.add("J_" + id);
        }
        try {
            //将所有id拼接，用一个请求查出所有商品的价格
            String jsonPrice = doGet(PRICE_URL + StringUtils.join(ids, ','));

            //解析 jsonData
            //先查看jsonData的结构，是数组形式
            ArrayNode arrayNode = (ArrayNode) MAPPER.readTree(jsonPrice);
            //遍历每个结点，截取id   "id":"J_100000982034"
            for(JsonNode jsonNode : arrayNode) {
                //截取_之后的字符，再转换为long型
                Long id = Long.valueOf(StringUtils.substringAfter(jsonNode.get("id").asText(), "_"));
                items.get(id).setPrice(jsonNode.get("p").asLong());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        //获取商品卖点（广告）
        ids = new ArrayList<String>();
        for(Long id : items.keySet()) {
            ids.add("AD_" + id);
        }
        try {
            String jsonAd = doGet(AD_URL + StringUtils.join(ids, ','));

            ArrayNode arrayNode = (ArrayNode) MAPPER.readTree(jsonAd);

            for(JsonNode jsonNode : arrayNode) {
                Long id = Long.valueOf(StringUtils.substringAfter(jsonNode.get("id").asText(), "_"));
                String ad = jsonNode.get("ad").asText();
                items.get(id).setSellPoint(ad);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return items.values();
    }

    /**
     * 将该页数据保存到数据库的商品表中
     * @param items
     */
    public void saveDataToDB(Collection<Item> items){
        itemMapper.saveItems(items);
    }

    /**
     * 开始抓取数据
     * @throws Exception
     */
    public void start() throws Exception {
        //获取总页数
        Integer totalPage = getTotalPage();

        //分页获取数据
        for (int i = 1; i <= totalPage; i++){
            LOGGER.info("当前第-{}-页，总共-{}-页。", i, totalPage);

            //获得该页所有商品数据的集合 items
            Collection<Item> items = getPageItems(i);

            if(items == null){
                LOGGER.info("抓到 0 条数据！");
                //这里为了方便测试，故使用break
                break;
                //继续抓下一页
                //continue;
            }

            LOGGER.info("在第-{}-页抓到-{}-条数据", i, items.size());

            //将数据保存到数据库
            LOGGER.info("开始保存第-{}-页的数据......", i);
            saveDataToDB(items);
            LOGGER.info("第-{}-页的数据保存完毕！", i);

            //TODO 这里为方便测试，故添加break
            break;

        }
    }

    /**
     * 发送 GET 请求，获取内容
     * @param url 请求的url
     * @return 返回获取的内容，无内容则返回null
     * @throws IOException
     */
    private String doGet(String url) throws IOException {
        //创建 httpclient 对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //创建 httpGet 请求
        HttpGet httpGet = new HttpGet(url);

        CloseableHttpResponse response = null;

        try {
            //执行请求，获得响应数据
            response = httpClient.execute(httpGet);
            //判断返回状态是否为200
            if(response.getStatusLine().getStatusCode() == 200){
                String content = EntityUtils.toString(response.getEntity(), "UTF-8");
                return content;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(response != null){
                response.close();
            }
            httpClient.close();
        }

        return null;
    }
}

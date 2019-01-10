package com.crawler.jd;

import com.crawler.pojo.Item;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.commons.io.FileUtils;
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author coldsmoke
 * @version 1.0
 * @className: PhoneJdCrawler
 * @description: 爬取手机相关信息
 * @date 2019/1/10 9:52
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

    public void start() throws Exception {

        final String BASE_URL = "https://list.jd.com/list.html?cat=9987,653,655&page=";
        final ObjectMapper MAPPER = new ObjectMapper();

        //入口页面地址
        String startUrl = BASE_URL + 1;

        //发送请求，获得响应内容html
        String html = doGet(startUrl);

        //解析html
        Document document = Jsoup.parse(html);

        //获取总页数
        String pageText = document.select("#J_topPage").text();
        //\\d+这个是由两部分组成的，\ 转义符 加\d 是一个正字表达式,\d 表示匹配的是数字,+ 表示重复一次或者多次
        String[] strs = pageText.split("\\D+");
        Integer totalPage = Integer.parseInt(strs[1]);

        StringBuilder sb = new StringBuilder();

        //分页获取数据
        for (int i = 1; i <= totalPage; i++){
            //获取url
            String url = BASE_URL + i;
            //发送请求，获取该页所有商品数据
            String content = doGet(url);
            //解析html
            Document root = Jsoup.parse(content);
            //该页所有商品信息
            Elements lis = root.select("#plist li.gl-item");

            /**
             * 保存商品信息
             */
            Map<Long, Item> items = new HashMap<Long, Item>();

            //对商品数据逐个进行解析
            for(Element li : lis){
                Item item = new Item();

                //建立div对象
                Element div = li.child(0);
                //获得商品Id
                Long id = Long.valueOf(div.attr("data-sku"));
                //获得商品图片地址, 排列在前的商品图片不是懒加载，排列在后的是懒加载
                String img = li.select(".p-img img").attr("src");
                if (img.trim().length() == 0){
                    img = li.select(".p-img img").attr("data-lazy-img");
                }
                //获得商品标题
                String title = li.select(".p-name").text();
                //获得商品价格，由于页面源代码中并没有直接给出价格，说明价格是页面加载完之后，又异步进行加载的
                //String price = li.select(".p-price").attr("J_price");

                item.setId(id);
                item.setImage(img);
                item.setTitle(title);

                items.put(id, item);
                //System.out.println(title);
                //TODO
                //break;
            }

            //获得商品价格,通过id，构造url，查询对应价格
            List<String> ids = new ArrayList<String>();
            for(Long id : items.keySet()){
                ids.add("J_" + id);
            }
            //将所有id拼接，用一个请求查出所有商品的价格
            String priceUrl = "https://p.3.cn/prices/mgets?&skuIds=" + StringUtils.join(ids, ',');
            String jsonData = doGet(priceUrl);
            //解析 jsonData
            //先查看jsonData的结构，是数组形式
            ArrayNode arrayNode = (ArrayNode) MAPPER.readTree(jsonData);
            //遍历每个结点，截取id   "id":"J_100000982034"
            for(JsonNode jsonNode : arrayNode) {
                //截取_之后的字符，再转换为long型
                Long id = Long.valueOf(StringUtils.substringAfter(jsonNode.get("id").asText(), "_"));
                items.get(id).setPrice(jsonNode.get("p").asLong());
            }

            //获取商品卖点（广告）
            ids.clear();
            for(Long id : items.keySet()) {
                ids.add("AD_" + id);
            }
            String adUrl = "https://ad.3.cn/ads/mgets?&skuids=" + StringUtils.join(ids, ',');
            jsonData = doGet(adUrl);
            arrayNode = (ArrayNode) MAPPER.readTree(jsonData);
            for(JsonNode jsonNode : arrayNode) {
                Long id = Long.valueOf(StringUtils.substringAfter(jsonNode.get("id").asText(), "_"));
                items.get(id).setSellPoint(jsonNode.get("ad").asText());
            }

            //把对象转换成 json 字符串，方便在 JsonView中查看结果
            String jsonItems = MAPPER.writeValueAsString(items);
            sb.append(jsonItems);
            //输出item
//            for(Item item : items.values()) {
//                //System.out.println(item);
//                //输出到文件
//                sb.append(item.toString() + "\n");
//            }
            //TODO
//            break;

        }

        FileUtils.writeStringToFile(new File("E:\\items.txt"), sb.toString(), "UTF-8");
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

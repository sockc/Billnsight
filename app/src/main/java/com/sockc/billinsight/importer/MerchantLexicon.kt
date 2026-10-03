package com.sockc.billinsight.importer

import java.util.Locale

/**
 * Offline merchant dictionary. Merchant terms indicate the business, while description
 * hints indicate a specific purchase (e.g. 通行费). Never merge merchant identities
 * merely because they share a keyword; aliases are explicit user decisions.
 *
 * Put specific brands before generic names via match strength, then prefer the
 * longest matching term. Conflicting equal-strength categories remain unclassified.
 */
object MerchantLexicon {
    private data class Entry(
        val category: String,
        val merchants: List<String>,
        val keywords: List<String>,
    )
    private fun terms(value: String): List<String> = value.split('|')
        .map { normalize(it) }.filter { it.length >= 2 }.distinct()

    private val prefixes = listOf(
        "微信支付", "支付宝支付", "支付宝", "财付通", "支付给", "付款给", "收款方",
        "交易对方", "商户名称", "商家名称"
    )

    private val entries = listOf(
        Entry("餐饮", terms("肯德基|麦当劳|华莱士|汉堡王|德克士|必胜客|达美乐|塔斯汀|尊宝比萨|真功夫|老乡鸡|乡村基|大米先生|永和大王|吉野家|和府捞面|李先生牛肉面|味千拉面|沙县小吃|兰州拉面|杨国福|张亮麻辣烫|海底捞|呷哺呷哺|小龙坎|蜀大侠|怂火锅|九毛九|太二酸菜鱼|鱼你在一起|遇见小面|五谷渔粉|三津汤包|老娘舅|袁记云饺|喜家德|西贝莜面村|老碗会|木屋烧烤|探鱼|蛙来哒|紫燕百味鸡|绝味鸭脖|周黑鸭|煌上煌|陶陶居|广州酒家|点都德|绿茶餐厅|外婆家|费大厨|农耕记|炳胜|乐凯撒|美团外卖|饿了么外卖|美团餐饮|美团买单|粤吃越湘|叹茶靓点"), terms("热干面|猪脚饭|隆江猪脚饭|煲仔饭|黄焖鸡|炒粉|炒面|炒饭|肠粉|云吞|馄饨|水饺|蒸饺|粥店|粥铺|湘厨|川菜馆|湘菜馆|小炒店|家常菜馆|社区厨房|茶餐厅|饭店|餐厅|餐馆|食堂|快餐|小吃|拉面|汤面|面馆|粉面|米线|螺蛳粉|麻辣烫|麻辣香锅|烧烤|烤鱼|火锅|烤肉|串串|寿司|料理店|包子铺|包点|早餐店|夜宵店|熟食店|卤味|卤菜|炸鸡|汉堡店|披萨店|饺子馆|饺子店|烧腊|烧鹅|叉烧|酸菜鱼|猪肚鸡|牛肉面|盖浇饭|汤粉|肠粉店|牛杂|鸡公煲|餐饮店|便当|快餐店|烘焙店|面包店|蛋糕店|面点|糕点店|粤菜馆|湘菜|湘味|川菜|家常小炒|农家小炒|饭庄|酒楼|食府|美食店|美食城|美食馆|快餐档|早餐档|茶楼|茶点|早茶|点心店|点心铺|现做点心|广式点心|蒸点|烧味|餐吧|饭馆|卤水店|炖汤店")),
        Entry("饮品", terms("瑞幸咖啡|luckincoffee|库迪咖啡|星巴克|星巴克咖啡|幸运咖|mannercoffee|MStand|SeesawCoffee|霸王茶姬|蜜雪冰城|古茗|茶百道|喜茶|奈雪的茶|沪上阿姨|一点点奶茶|CoCo都可|益禾堂|书亦烧仙草|茶颜悦色|茶理宜世|茉莉奶白|柠季|林里手打柠檬茶|阿嬷手作|甜啦啦|爷爷不泡茶|茶话弄|柠檬茶研究所|百分茶|贡茶|悸动烧仙草|都可茶饮|茶救星球"), terms("奶茶店|鲜榨果汁|鲜果茶|柠檬茶|果茶店|果汁店|咖啡馆|咖啡店|咖啡屋|茶饮店|手打柠檬茶|烧仙草|鲜奶茶|奶盖茶|手冲咖啡|咖啡饮品|奶昔|饮品店|豆浆店|凉茶铺|冰淇淋店|雪糕店|甜品店|奶茶|茶饮|果饮|柠檬饮|鲜果茶|咖啡|凉茶店|糖水铺|糖水店|茶饮铺|茶饮屋|甜水铺")),
        Entry("商超日用", terms("沃尔玛|山姆会员店|永辉超市|盒马鲜生|华润万家|大润发|家乐福|物美超市|北京华联|联华超市|世纪联华|家家悦|人人乐|麦德龙|朴朴超市|叮咚买菜|多点超市|天虹超市|中百仓储|中百超市|美宜佳|7eleven|7-eleven|全家便利店|罗森便利店|十足便利店|便利蜂|天福便利店|喜士多|见福便利店|易捷便利店|昆仑好客|屈臣氏|万宁|名创优品|无印良品|好特卖|好想来零食|赵一鸣零食|零食很忙|零食有鸣|好邻居便利店|志兴生活超市|拾贰便利店|新永佳百货|缤纷生鲜汇"), terms("便利店|生活超市|生鲜超市|社区超市|百货超市|平价超市|折扣超市|连锁超市|综合超市|日用百货|日杂店|百货店|百货商行|日用品店|杂货铺|食杂店|小卖部|士多店|烟酒商行|粮油店|杂货店|零食店|折扣店|母婴店|生活馆|卫生纸|洗衣液|洗洁精|日化用品|百货商店|商超|购物超市|超市|百货|生鲜汇|鲜生汇|生鲜馆|生鲜店|生鲜市场|生鲜便利|生鲜生活|生鲜商店|便民超市|社区便利|便民店|副食店|副食商行|生活百货|日用商行|平价百货|综合百货|粮油副食|副食品店")),
        Entry("购物", terms("淘宝商城|天猫商城|京东商城|京东自营|京东购物|拼多多商城|拼多多购物|唯品会|抖音商城|快手小店|苏宁易购|国美电器|得物购物|小红书商城|京东国际|天猫国际|1688采购|阿里巴巴批发|网易严选|小米商城|华为商城|苹果官网|AppleStore|优衣库|海澜之家|太平鸟|森马服饰|安踏专卖|李宁专卖|耐克专卖|阿迪达斯专卖|迪卡侬|特步专卖|鸿星尔克|蕉下|URBANREVIVO|ZARA|H&M|GAP服装|无印良品服装|名创优品商城"), terms("服装店|服饰店|鞋店|鞋服|箱包店|皮具店|眼镜店|珠宝店|饰品店|玩具店|文具店|五金店|家具店|家居店|家纺店|电器商场|电器专卖|家电卖场|床上用品|网购订单|商品购物|电商购物|商城购物|线上购物|网店购物|官方旗舰店|旗舰店购物|家具|家具配件|家具连接件|三合一连接件|柜体连接件|家居五金|衣柜五金|橱柜五金|五金配件|家具五金|连接件柜|床架配件|家居建材|柜门配件|家具安装|木工配件")),
        Entry("交通", terms("滴滴出行|滴滴快车|滴滴打车|高德打车|曹操出行|T3出行|首汽约车|享道出行|哈啰出行|哈啰单车|青桔单车|美团单车|美团打车|花小猪打车|阳光出行|如祺出行|嘀嗒出行|携程火车票|铁路12306|中国铁路客户服务中心|广州地铁|深圳地铁|北京地铁|上海地铁|广州公交|深圳公交|车来了|羊城通|岭南通|深圳通|北京一卡通|上海交通卡|滴滴顺风车|顺风车订单"), terms("公交车|公交充值|地铁乘车|地铁票|公交票|出租车|网约车|打车订单|顺风车|火车票|高铁票|动车票|轻轨票|乘车码|公交乘车|交通卡|轮渡票|客运票|船票|出行订单|火车站售票")),
        Entry("车辆", terms("小桔充电|小桔能源|特来电|星星充电|国家电网充电|南方电网充电|开迈斯|蔚来加电|小鹏充电|理想充电|云快充|万城万充|依威能源|充电桩订单|广东联合电子服务|联合电子服务|粤通卡|ETC助手|高速通行费|高速收费|高速公路收费|无感停车|捷顺停车|停简单|ETCP停车|小猫停车|车行易|途虎养车|天猫养车|京东养车|车点点|一嗨租车|神州租车|联动云租车"), terms("通行费|高速费|路桥费|停车费|停车场收费|停车订单|洗车服务|洗车店|车辆保养|汽车维修|汽车美容|汽车修理|道路救援|充电桩|新能源充电|机动车检测|年检服务|汽车保险|车险缴费|汽车租赁|轮胎店|补胎店|汽车配件")),
        Entry("加油", terms("中国石化|中国石油|中石化加油|中石油加油|壳牌加油|延长石油|中海油加油|道达尔加油|中化石油|民营加油站|易捷加油|团油|滴滴加油"), terms("加油站|加油订单|油费充值|汽车加油|车辆加油|汽油费|柴油费")),
        Entry("生活缴费", terms("中国移动|中国移动通信集团|中国联通|中国联合网络通信|中国电信|中国电信股份|中国广电|移动营业厅|联通营业厅|电信营业厅|南方电网|国家电网|广州供电局|深圳供电局|中国燃气|华润燃气|新奥燃气|广州自来水|深圳水务|中国水务|广东电网|深圳燃气|广东广电网络"), terms("话费充值|手机话费|手机充值|手机缴费|话费缴费|流量充值|流量套餐|宽带续费|宽带缴费|光纤宽带|水费缴纳|电费缴纳|电费充值|燃气缴费|燃气充值|天然气缴费|网络通信费|有线电视费|电话费|生活缴费|固话费")),
        Entry("水果", terms("百果园|鲜丰水果|果多美|水果好芒|绿叶水果|钱大妈水果|叶氏兄弟水果|天鲜果业|鲜果切|榴莲专卖|水果捞店"), terms("水果店|水果摊|水果超市|鲜果店|水果商行|果业|果园|果蔬店|鲜果切|水果捞|榴莲店|水果批发|苹果水果|荔枝专卖|西瓜店|水果配送|鲜果汇|鲜果坊|果蔬汇|水果行|水果铺|水果档|水果专卖|鲜果批发")),
        Entry("买菜", terms("钱大妈|谊品生鲜|菜市场|农贸市场|蔬菜批发市场|肉联厂|生鲜配送|美团买菜|叮咚买菜生鲜|盒马菜场"), terms("菜市场|农贸市场|蔬菜店|蔬菜摊|菜摊|买菜|鲜肉店|猪肉店|牛肉店|活禽店|水产店|海鲜市场|鱼档|肉档|粮油生鲜|菜场|菜篮子|生鲜菜店|鸡蛋店|菜肉店|肉菜市场|菜篮子超市|瓜果蔬菜|活鲜市场|冻品批发|蔬果摊|农产品市场")),
        Entry("住房", terms("链家租房|贝壳租房|自如租房|我爱我家租房|蛋壳公寓|万科物业|碧桂园物业|保利物业|绿城物业|龙湖物业|金地物业"), terms("房租|房屋租金|房屋中介费|公寓租金|物业管理费|小区物业费|物业缴费|房屋维修基金|租房押金|停车位租金|房租转账")),
        Entry("医疗", terms("大参林|益丰大药房|老百姓大药房|海王星辰|一心堂|国大药房|健之佳|漱玉平民|叮当快药|京东健康药房|阿里健康大药房|同仁堂药店|康爱多药房|北京协和医院|中山大学附属医院|广东省人民医院|南方医院|广州医科大学附属医院"), terms("医院门诊|医院挂号|门诊挂号|门诊费|药房|药店|诊所|卫生站|卫生院|体检中心|医学检验|牙科诊所|口腔医院|眼科医院|住院费用|医疗费用|处方药|买药订单|医药费")),
        Entry("教育", terms("新东方教育|学而思|猿辅导|作业帮课程|高途课堂|掌门一对一|粉笔教育|中公教育|华图教育|中国大学MOOC|网易云课堂|得到课程|知乎知学堂|樊登读书|微信读书会员|喜马拉雅课程"), terms("学校学费|学杂费|培训费|辅导班|幼儿园学费|教育培训|网课购买|课程费用|驾校学费|考试报名费|教材费|书本费|补课费|图书购买|书店购书|技能培训|兴趣班")),
        Entry("娱乐", terms("腾讯视频|爱奇艺|优酷视频|芒果TV|哔哩哔哩大会员|网易云音乐会员|QQ音乐会员|酷狗音乐会员|喜马拉雅会员|猫眼电影|淘票票|万达影城|大地影院|CGV影城|横店电影城|保利国际影城|星聚会KTV|纯K|唱吧麦颂|王者荣耀充值|和平精英充值|Steam商店|NintendoeShop|PlayStationStore"), terms("电影院|电影票|影城购票|视频会员|音乐会员|游戏平台|游戏充值|演出票|音乐节门票|话剧门票|KTV消费|密室逃脱|剧本杀|桌游馆|游乐园门票|健身房|体育场馆|游泳馆门票")),
        Entry("数码", terms("苹果直营店|AppleStore|小米之家|华为体验店|荣耀体验店|OPPO官方商城|vivo官方商城|三星商城|联想商城|戴尔商城|华硕商城|京东数码|苹果授权店|小米授权店|华为授权店"), terms("手机维修|手机配件|手机专卖|电脑维修|电脑配件|平板电脑|相机专卖|数码产品|耳机购买|电子产品|笔记本电脑|电脑专卖|数码店|电子城|手机商城")),
        Entry("旅行", terms("携程旅行|飞猪旅行|去哪儿旅行|同程旅行|途牛旅游|马蜂窝旅游|华住会|全季酒店|汉庭酒店|亚朵酒店|如家酒店|锦江之星|格林豪泰|维也纳酒店|希尔顿酒店|万豪酒店|洲际酒店|美团酒店|Airbnb爱彼迎"), terms("酒店预订|酒店住宿|旅馆住宿|民宿住宿|旅行团费|旅游订单|景区门票|景点门票|旅游服务|签证服务|机票预订|国际机票|度假酒店|出游套餐")),
        Entry("经营相关", terms("阿里云|腾讯云|华为云|百度智能云|Cloudflare|Namecheap|Dynadot|Vultr|Hetzner|DigitalOcean|AWS云服务|京东云|七牛云|又拍云"), terms("云服务器|服务器租赁|域名续费|域名注册|云主机|SSL证书购买|进货货款|采购货款|货物运输费|摊位租金|仓库租赁|经营费用|营业耗材|商用收款机")),
        Entry("人情", terms("婚宴礼金|生日礼金|乔迁礼金|满月礼金|随礼|礼金转账|慰问金|红包礼金"), terms("礼金|份子钱|婚礼红包|节日红包|生日红包|乔迁红包|人情往来|丧礼礼金"))
    )


    fun normalize(raw: String): String {
        var cleaned = raw.trim().lowercase(Locale.ROOT)
        repeat(3) {
            val prefix = prefixes.firstOrNull { cleaned.startsWith(it) }
            if (prefix != null) {
                cleaned = cleaned.removePrefix(prefix).trimStart(' ', '：', ':', '-', '—', '_')
            }
        }
        return cleaned.replace(Regex("[\\s：:()（）\\[\\]【】_\\-—·]+"), "")
    }

    private data class Hit(val category: String, val score: Int)

    private fun match(text: String, terms: List<String>, category: String, base: Int): Hit? {
        val length = terms.filter { text.contains(it) }.maxOfOrNull { it.length } ?: return null
        return Hit(category, base + length)
    }

    private fun choose(hits: List<Hit>): String? {
        val highest = hits.maxOfOrNull { it.score } ?: return null
        return hits.filter { it.score == highest }.map { it.category }.distinct()
            .singleOrNull()
    }

    /**
     * Recognize spending only; callers determine direction/repayments/transfers first.
     * Do not infer a shop merely from a QR-code placeholder or the payment platform.
     */
    fun suggest(merchant: String, description: String = ""): String? {
        val name = normalize(merchant)
        val detail = normalize(description)
        val merchantHits = if (name.isBlank() ||
            ScanPaymentClassifier.isGenericCounterparty(merchant)) emptyList() else
            entries.flatMap { entry ->
                listOfNotNull(
                    match(name, entry.merchants, entry.category, 1000),
                    match(name, entry.keywords, entry.category, 500)
                )
            }
        // Descriptions can disambiguate a multi-service platform, but generic
        // "扫码付款" / "充电" / "充值" alone are not purchase evidence.
        val detailHits = if (detail.isBlank()) emptyList() else
            entries.flatMap { entry ->
                listOfNotNull(
                    match(detail, entry.merchants, entry.category, 300),
                    match(detail, entry.keywords, entry.category, 200)
                )
            }
        return choose(merchantHits + detailHits)
    }

    val termCount: Int by lazy {
        entries.flatMap { it.merchants + it.keywords }.distinct().size
    }
}

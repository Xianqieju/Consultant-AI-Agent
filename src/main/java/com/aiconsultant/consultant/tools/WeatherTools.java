package com.aiconsultant.consultant.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("weatherTools")
public class WeatherTools implements BaseAgentTool{

    @Tool("查询指定城市或地区的当前天气状况。在为用户生成包含环境描写的笔记、日记或情绪分析前，必须调用此工具获取现实环境数据。")
    public String getWeather(
            @P("城市名称，例如'北京市'、'上海市'") String city) {

        log.info("Agent 触发天气查询 Tool, 目标城市: {}", city);

        // 这里可以对接真实的天气 API（如高德地图、和风天气），目前先模拟返回
        // 刻意返回带有情绪价值的天气描述，方便大模型做文章
        return city + "当前天气：晴朗，气温 24°C，微风和煦，阳光明媚，是适合放松心情的好天气。";
    }
}
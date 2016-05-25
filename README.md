# flume-ng-spool-extends

使用配置:


指定入口

a1.sources.s.type = com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySource

a1.sources.s.channels = c

a1.sources.s.spoolDir = /home/hadoop/agent_log

a1.sources.s.fileHeader = true

指定正则表达式，如果满足表达是即为一条日志的开始

a1.sources.s.datePattern = ^\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}

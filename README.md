# flume-ng-spool-extends

使用配置
#agent section  
a1.sources = s
a1.channels = c
a1.sinks = r

#source
#指定入口
a1.sources.s.type = com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySource
a1.sources.s.channels = c
a1.sources.s.spoolDir = /home/hadoop/agent_log
a1.sources.s.fileHeader = true
#指定正则表达式，如果满足表达是即为一条日志的开始
a1.sources.s.datePattern = ^\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}

# a1 FileChannel
a1.channels.c.type = file
a1.channels.c.checkpointDir = /home/hadoop/checkpoint
a1.channels.c.dataDirs = /home/hadoop/data
a1.channels.c.capacity = 200000000
a1.channels.c.keep-alive = 30
a1.channels.c.write-timeout = 30
a1.channels.c.checkpoint-timeout=600

#sink
a1.sinks.r.type = org.apache.flume.sink.kafka.KafkaSink
a1.sinks.r.topic = spool
a1.sinks.r.brokerList = 192.168.1.241:9092
a1.sinks.r.requiredAcks = 1
a1.sinks.r.batchSize = 20
a1.sinks.r.channel = c